package com.devtrace.global.exception;

import com.devtrace.analysis.exception.AiAnalysisException;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice

/** 요청 검증, 데이터베이스 및 AI 오류를 공통 JSON 오류 응답으로 변환한다. */
public class ApiExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    
/** 요청 필드 검증 실패를 공통 400 JSON 응답으로 변환한다. */
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException exception) {
        List<ApiErrorResponse.FieldError> errors = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new ApiErrorResponse.FieldError(error.getField(), error.getDefaultMessage()))
                .collect(Collectors.toList());
        return badRequest(errors);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    
/** 읽을 수 없는 JSON 요청을 공통 400 JSON 응답으로 변환한다. */
    public ResponseEntity<ApiErrorResponse> handleUnreadableJson(HttpMessageNotReadableException exception) {
        return badRequest(List.of(new ApiErrorResponse.FieldError(
                "request", "요청 본문이 없거나 JSON 형식이 올바르지 않습니다.")));
    }

    @ExceptionHandler(DataAccessException.class)
    
/** 데이터베이스 예외 상세를 노출하지 않고 공통 오류 응답으로 변환한다. */
    public ResponseEntity<ApiErrorResponse> handleDatabaseFailure(DataAccessException exception) {
        var error = new ApiErrorResponse.FieldError("request", "요청 내용을 저장하지 못했습니다. 잠시 후 다시 시도해 주세요.");
        var response = new ApiErrorResponse(false, "오류 접수 저장에 실패했습니다.", List.of(error));
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    @ExceptionHandler(AiAnalysisException.class)
    
/** AI 분석 실패 유형을 안전한 공통 오류 응답으로 변환한다. */
    public ResponseEntity<ApiErrorResponse> handleAiAnalysisFailure(AiAnalysisException exception) {
        HttpStatus status = switch (exception.failure()) {
            case TIMEOUT -> HttpStatus.GATEWAY_TIMEOUT;
            case UPSTREAM, INVALID_RESPONSE -> HttpStatus.BAD_GATEWAY;
        };
        String message = switch (exception.failure()) {
            case TIMEOUT -> "AI 분석 응답 시간이 초과되었습니다. 잠시 후 다시 시도해 주세요.";
            case UPSTREAM -> "AI 분석 서비스에 연결하지 못했습니다.";
            case INVALID_RESPONSE -> "AI 분석 결과를 확인할 수 없습니다.";
        };
        return ResponseEntity.status(status)
                .body(new ApiErrorResponse(false, message, List.of()));
    }

    private ResponseEntity<ApiErrorResponse> badRequest(List<ApiErrorResponse.FieldError> errors) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiErrorResponse(false, "입력값을 확인해 주세요.", errors));
    }
}
