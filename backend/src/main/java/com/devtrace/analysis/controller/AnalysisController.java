package com.devtrace.analysis.controller;

import com.devtrace.analysis.dto.*;
import com.devtrace.analysis.exception.AiAnalysisException;
import com.devtrace.analysis.parser.JavaLogParser;
import com.devtrace.analysis.parser.SensitiveDataMasker;
import com.devtrace.analysis.repository.AnalysisRepository;
import com.devtrace.analysis.service.AiAnalysisService;
import com.devtrace.global.exception.ApiErrorResponse;
import com.devtrace.login.domain.UserPrincipal;

import java.util.List;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DataAccessException;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/analyses")

/** 분석 등록·목록·상세·삭제, 로그 파싱 및 동의 기반 AI 분석 HTTP 요청을 처리한다. */
public class AnalysisController {
    private final AnalysisRepository analysisRepository;
    private final JavaLogParser javaLogParser;
    private final SensitiveDataMasker sensitiveDataMasker;
    private final AiAnalysisService aiAnalysisService;
    private final ObjectMapper objectMapper;

    public AnalysisController(
            AnalysisRepository analysisRepository,
            JavaLogParser javaLogParser,
            SensitiveDataMasker sensitiveDataMasker,
            AiAnalysisService aiAnalysisService,
            ObjectMapper objectMapper) {
        this.analysisRepository = analysisRepository;
        this.javaLogParser = javaLogParser;
        this.sensitiveDataMasker = sensitiveDataMasker;
        this.aiAnalysisService = aiAnalysisService;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    
/** 인증 사용자의 분석 목록만 조회한다. */
    public List<AnalysisListItem> listAnalyses(@AuthenticationPrincipal UserPrincipal principal) {
        return analysisRepository.findAll(principal.id());
    }

    @GetMapping("/{id}")
    
/** 요청한 분석 ID가 인증 사용자 소유일 때 상세 정보를 반환한다. */
    public ResponseEntity<AnalysisDetailResponse> getAnalysis(
            @PathVariable long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        return analysisRepository.findDetailByIdAndUserId(id, principal.id())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/notes")
    public ResponseEntity<AnalysisResolutionNoteResponse> getResolutionNote(
            @PathVariable long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        return analysisRepository.findResolutionNoteByIdAndUserId(id, principal.id())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @org.springframework.web.bind.annotation.PutMapping("/{id}/notes")
    public ResponseEntity<AnalysisResolutionNoteResponse> saveResolutionNote(
            @PathVariable long id,
            @Valid @RequestBody AnalysisResolutionNoteRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        if (!analysisRepository.saveResolutionNote(id, principal.id(), request)) {
            return ResponseEntity.notFound().build();
        }
        return analysisRepository.findResolutionNoteByIdAndUserId(id, principal.id())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    
/** 인증 사용자 소유의 분석만 삭제하고 결과 상태를 반환한다. */
    public ResponseEntity<Void> deleteAnalysis(
            @PathVariable long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        if (!analysisRepository.deleteByIdAndUserId(id, principal.id())) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/parse-log")
    
/** 원본 로그를 서버에서 마스킹한 뒤 파싱 결과를 반환하며 DB에는 저장하지 않는다. */
    public LogParseResponse parseLog(@Valid @RequestBody LogParseRequest request) {
        var masking = sensitiveDataMasker.mask(request.logText());
        var parsed = javaLogParser.parse(masking.maskedLog());
        return new LogParseResponse(
                parsed.representativeError(),
                parsed.relatedExceptions(),
                parsed.keyLogs(),
                parsed.sourceLocations(),
                parsed.contextPreview(),
                masking.maskedLog(),
                masking.detections());
    }

    @PostMapping("/ai")
    
/** 동의와 요청을 확인하고 서버 측 재마스킹 후 분석 결과를 저장해 반환한다. */
    public ResponseEntity<?> prepareAiAnalysis(
            @Valid @RequestBody AiAnalysisRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        if (!Boolean.TRUE.equals(request.consent())) {
            return rejectedAiRequest();
        }
        if (!aiAnalysisService.mockModeEnabled() && (request.apiKey() == null || request.apiKey().isBlank())) {
            return rejectedAiRequest();
        }
        if (analysisRepository.findByIdAndUserId(request.analysisId(), principal.id()).isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        final SensitiveDataMasker.Result masking;
        final LogParseResponse parsed;
        try {
            masking = sensitiveDataMasker.mask(request.logText());
            if (masking == null || masking.maskedLog() == null || masking.maskedLog().isBlank()
                    || masking.detections() == null
                    || (request.apiKey() != null && !request.apiKey().isBlank()
                            && masking.maskedLog().contains(request.apiKey()))) {
                return rejectedAiRequest();
            }
// 로그와 파싱 신호 모두 서버에서 마스킹한 내용만 사용한다.
            parsed = javaLogParser.parse(masking.maskedLog());
        } catch (RuntimeException processingFailure) {
// 예외 상세에는 입력값이나 인증 정보가 포함될 수 있어 응답에 넣지 않는다.
            return rejectedAiRequest();
        }
        try {
            AiAnalysisResponse result = aiAnalysisService.analyze(
                    request.apiKey(), request.language(), masking.maskedLog(), parsed);
            if (result == null) {
                throw new AiAnalysisException(AiAnalysisException.Failure.INVALID_RESPONSE);
            }
            String resultJson = objectMapper.writeValueAsString(result);
            if (!analysisRepository.saveAiResultAndComplete(request.analysisId(), principal.id(), resultJson)) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(result);
        } catch (AiAnalysisException failure) {
            analysisRepository.markAiAnalysisFailed(request.analysisId(), principal.id());
            throw failure;
        } catch (JsonProcessingException serializationFailure) {
            analysisRepository.markAiAnalysisFailed(request.analysisId(), principal.id());
            return ResponseEntity.internalServerError()
                    .body(new ApiErrorResponse(false, "AI 분석 결과를 저장하지 못했습니다.", List.of()));
        } catch (DataAccessException persistenceFailure) {
// 트랜잭션 저장 실패를 되돌린 뒤 FAILED 상태를 별도로 기록한다.
            analysisRepository.markAiAnalysisFailed(request.analysisId(), principal.id());
            throw persistenceFailure;
        }
    }

    private ResponseEntity<ApiErrorResponse> rejectedAiRequest() {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiErrorResponse(false, "AI 분석 요청을 처리할 수 없습니다.", List.of()));
    }

    @PostMapping
    
/** 인증 사용자 소유로 분석 요청을 저장하고 접수 정보를 반환한다. */
    public ResponseEntity<AnalysisResponse> createAnalysis(
            @Valid @RequestBody AnalysisRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
// PostgreSQL 저장이 확인된 뒤에만 성공 응답을 반환한다.
        analysisRepository.save(request, principal.id());
        var data = new AnalysisResponse.Data(
                request.language(),
                request.errorMessage(),
                request.errorMessage().length(),
                "PENDING");
        var response = new AnalysisResponse(
                true,
                "오류 메시지가 백엔드에 정상적으로 전달되었습니다.",
                data);
        return ResponseEntity.ok(response);
    }
}
