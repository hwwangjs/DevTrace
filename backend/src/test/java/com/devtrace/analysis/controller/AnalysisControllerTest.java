package com.devtrace.analysis.controller;

import com.devtrace.analysis.dto.*;
import com.devtrace.analysis.exception.AiAnalysisException;
import com.devtrace.analysis.parser.*;
import com.devtrace.analysis.repository.AnalysisRepository;
import com.devtrace.analysis.service.AiAnalysisService;
import com.devtrace.global.controller.HelloController;
import com.devtrace.global.exception.ApiErrorResponse;
import com.devtrace.login.domain.*;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@WebMvcTest(controllers = {AnalysisController.class, HelloController.class})
@AutoConfigureMockMvc(addFilters = false)

/** 분석 API의 요청·응답, 사용자 범위, 파싱 및 AI 처리 동작을 검증한다. */
class AnalysisControllerTest {
    private static final long USER_A_ID = 41L;
    private static final long USER_B_ID = 52L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AnalysisRepository analysisRepository;

    @MockitoBean
    private JavaLogParser javaLogParser;

    @MockitoBean
    private SensitiveDataMasker sensitiveDataMasker;

    @MockitoBean
    private AiAnalysisService aiAnalysisService;

    @BeforeEach
    void authenticateAsUserA() {
        authenticateAs(USER_A_ID);
        var owned = new AnalysisListItem(7L, "Java", "existing", "PENDING",
                OffsetDateTime.parse("2026-10-30T12:00:00Z"));
        when(analysisRepository.findByIdAndUserId(anyLong(), org.mockito.ArgumentMatchers.eq(USER_A_ID)))
                .thenReturn(java.util.Optional.of(owned));
        when(analysisRepository.saveAiResultAndComplete(
                org.mockito.ArgumentMatchers.eq(7L), org.mockito.ArgumentMatchers.eq(USER_A_ID), any(String.class)))
                .thenReturn(true);
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void listsAnalysisRequestsWithTheirResponseFields() throws Exception {
        var createdAt = OffsetDateTime.parse("2026-10-30T12:00:00Z");
        when(analysisRepository.findAll(USER_A_ID)).thenReturn(List.of(
                new AnalysisListItem(7, "Java", "NullPointerException", "PENDING", createdAt)));

        mockMvc.perform(get("/api/analyses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(7))
                .andExpect(jsonPath("$[0].language").value("Java"))
                .andExpect(jsonPath("$[0].errorMessage").value("NullPointerException"))
                .andExpect(jsonPath("$[0].analysisStatus").value("PENDING"))
                .andExpect(jsonPath("$[0].createdAt").value("2026-10-30T12:00:00Z"));

        verify(analysisRepository).findAll(USER_A_ID);
    }

    @Test
    void listsOnlyForTheCurrentlyAuthenticatedUser() throws Exception {
        var createdAt = OffsetDateTime.parse("2026-10-30T12:00:00Z");
        when(analysisRepository.findAll(USER_A_ID)).thenReturn(List.of(
                new AnalysisListItem(7, "Java", "A error", "PENDING", createdAt)));
        when(analysisRepository.findAll(USER_B_ID)).thenReturn(List.of(
                new AnalysisListItem(8, "Java", "B error", "PENDING", createdAt)));

        mockMvc.perform(get("/api/analyses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].errorMessage").value("A error"));

        authenticateAs(USER_B_ID);
        mockMvc.perform(get("/api/analyses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].errorMessage").value("B error"));

        verify(analysisRepository).findAll(USER_A_ID);
        verify(analysisRepository).findAll(USER_B_ID);
    }

    @Test
    void listsTwoAnalysesForAAndOneForBUsingEachAuthenticatedPrincipal() throws Exception {
        var createdAt = OffsetDateTime.parse("2026-10-30T12:00:00Z");
        when(analysisRepository.findAll(USER_A_ID)).thenReturn(List.of(
                new AnalysisListItem(7, "Java", "A error 1", "PENDING", createdAt),
                new AnalysisListItem(8, "Java", "A error 2", "PENDING", createdAt)));
        when(analysisRepository.findAll(USER_B_ID)).thenReturn(List.of(
                new AnalysisListItem(9, "Java", "B error", "PENDING", createdAt)));

        mockMvc.perform(get("/api/analyses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(2)))
                .andExpect(jsonPath("$[*].errorMessage", org.hamcrest.Matchers.containsInAnyOrder(
                        "A error 1", "A error 2")));

        authenticateAs(USER_B_ID);
        mockMvc.perform(get("/api/analyses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$[0].errorMessage").value("B error"));

        verify(analysisRepository).findAll(USER_A_ID);
        verify(analysisRepository).findAll(USER_B_ID);
    }

    @Test
    void returnsAnalysisDetailForItsOwner() throws Exception {
        var createdAt = OffsetDateTime.parse("2026-10-30T12:00:00Z");
        var item = new AnalysisListItem(7, "Java", "A error", "PENDING", createdAt);
        var detail = new AnalysisDetailResponse(7L, "Java", "A error", "PENDING", createdAt, null, null);
        when(analysisRepository.findDetailByIdAndUserId(7L, USER_A_ID)).thenReturn(java.util.Optional.of(detail));

        mockMvc.perform(get("/api/analyses/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.errorMessage").value("A error"));

        verify(analysisRepository).findDetailByIdAndUserId(7L, USER_A_ID);
        mockMvc.perform(get("/api/analyses/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aiAnalysisResult").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void detailResponseIncludesPersistedAiAnalysisWhenPresent() throws Exception {
        var createdAt = OffsetDateTime.parse("2026-10-30T12:00:00Z");
        var result = sampleAiResponse();
        when(analysisRepository.findDetailByIdAndUserId(7L, USER_A_ID)).thenReturn(java.util.Optional.of(
                new AnalysisDetailResponse(7L, "Java", "A error", "COMPLETED", createdAt, createdAt, result)));

        mockMvc.perform(get("/api/analyses/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.analysisStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.completedAt").value("2026-10-30T12:00:00Z"))
                .andExpect(jsonPath("$.aiAnalysisResult.summary").value(result.summary()));
    }

    @Test
    void returnsNotFoundForForeignAndNonexistentAnalysisIds() throws Exception {
        authenticateAs(USER_B_ID);
        when(analysisRepository.findDetailByIdAndUserId(7L, USER_B_ID)).thenReturn(java.util.Optional.empty());
        when(analysisRepository.findDetailByIdAndUserId(999L, USER_B_ID)).thenReturn(java.util.Optional.empty());

        mockMvc.perform(get("/api/analyses/7")).andExpect(status().isNotFound()).andExpect(content().string(""));
        mockMvc.perform(get("/api/analyses/999")).andExpect(status().isNotFound()).andExpect(content().string(""));

        verify(analysisRepository).findDetailByIdAndUserId(7L, USER_B_ID);
        verify(analysisRepository).findDetailByIdAndUserId(999L, USER_B_ID);
    }

    @Test
    void deletesAnalysisOnlyForItsOwner() throws Exception {
        when(analysisRepository.deleteByIdAndUserId(7L, USER_A_ID)).thenReturn(true);
        when(analysisRepository.deleteByIdAndUserId(7L, USER_B_ID)).thenReturn(false);

        mockMvc.perform(delete("/api/analyses/7")).andExpect(status().isNoContent());

        authenticateAs(USER_B_ID);
        mockMvc.perform(delete("/api/analyses/7")).andExpect(status().isNotFound());

        verify(analysisRepository).deleteByIdAndUserId(7L, USER_A_ID);
        verify(analysisRepository).deleteByIdAndUserId(7L, USER_B_ID);
    }

    @Test
    void analysisUpdateEndpointRemainsUnavailable() throws Exception {
        mockMvc.perform(put("/api/analyses/{id}", 7L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void parsesJavaLogWithoutReturningTheSubmittedRawLog() throws Exception {
        var parsed = new LogParseResponse(
                new LogParseResponse.LogLine(1, "ERROR java.lang.IllegalStateException"),
                List.of(new LogParseResponse.LogLine(3, "Caused by: java.io.IOException")),
                List.of(new LogParseResponse.LogLine(1, "ERROR java.lang.IllegalStateException")),
                List.of(new LogParseResponse.SourceLocation(2, "Service.java", 42, "com.devtrace.Service.run")),
                List.of(new LogParseResponse.LogLine(1, "ERROR java.lang.IllegalStateException")));
        when(sensitiveDataMasker.mask("ERROR java.lang.IllegalStateException"))
                .thenReturn(new SensitiveDataMasker.Result("ERROR java.lang.IllegalStateException",
                        List.of(new SensitiveDataMasker.Detection(1, "PASSWORD", 1))));
        when(javaLogParser.parse("ERROR java.lang.IllegalStateException")).thenReturn(parsed);
        mockMvc.perform(post("/api/analyses/parse-log")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"logText\":\"ERROR java.lang.IllegalStateException\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.representativeError.lineNumber").value(1))
                .andExpect(jsonPath("$.relatedExceptions[0].text").value("Caused by: java.io.IOException"))
                .andExpect(jsonPath("$.sourceLocations[0].fileName").value("Service.java"))
                .andExpect(jsonPath("$.sourceLocations[0].sourceLineNumber").value(42))
                .andExpect(jsonPath("$.maskedLogPreview").value("ERROR java.lang.IllegalStateException"))
                .andExpect(jsonPath("$.sensitiveDetections[0].type").value("PASSWORD"))
                .andExpect(jsonPath("$.logText").doesNotExist());
    }

    @Test
    void parseLogResponseDoesNotExposeTheOriginalSecret() throws Exception {
        String originalLog = "password=test-only-secret-value";
        String maskedLog = "password=[REDACTED]";
        when(sensitiveDataMasker.mask(originalLog)).thenReturn(new SensitiveDataMasker.Result(
                maskedLog, List.of(new SensitiveDataMasker.Detection(1, "PASSWORD", 1))));
        when(javaLogParser.parse(maskedLog)).thenReturn(new LogParseResponse(
                null, List.of(), List.of(), List.of(), List.of(), maskedLog,
                List.of(new SensitiveDataMasker.Detection(1, "PASSWORD", 1))));

        mockMvc.perform(post("/api/analyses/parse-log")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"logText\":\"%s\"}".formatted(originalLog)))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("test-only-secret-value"))))
                .andExpect(jsonPath("$.maskedLogPreview").value(maskedLog));

        verify(javaLogParser).parse(maskedLog);
    }

    @Test
    void rejectsBlankAndOversizedLogInput() throws Exception {
        mockMvc.perform(post("/api/analyses/parse-log")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"logText\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItem("logText")));

        String largeLog = "x".repeat(50_001);
        mockMvc.perform(post("/api/analyses/parse-log")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"logText\":\"%s\"}".formatted(largeLog)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItem("logText")));
    }

    @Test
    void rejectsAiPreparationWithoutExplicitConsent() throws Exception {
        mockAi("false", "safe log", "dummy-api-key").andExpect(status().isBadRequest())
                .andExpect(content().string(not(containsString("dummy-api-key"))));
        mockAi("null", "safe log", "dummy-api-key").andExpect(status().isBadRequest());
        verify(sensitiveDataMasker, never()).mask(any());
    }

    @Test
    void rejectsAiPreparationWithoutApiKey() throws Exception {
        mockAi("true", "safe log", "").andExpect(status().isBadRequest());
        verify(sensitiveDataMasker, never()).mask(any());
    }

    @Test
    void allowsMissingApiKeyOnlyWhenDevelopmentMockModeIsEnabled() throws Exception {
        String log = "ERROR NullPointerException in Example.run";
        var parsed = new LogParseResponse(null, List.of(), List.of(), List.of(), List.of());
        when(aiAnalysisService.mockModeEnabled()).thenReturn(true);
        when(sensitiveDataMasker.mask(log)).thenReturn(new SensitiveDataMasker.Result(log, List.of()));
        when(javaLogParser.parse(log)).thenReturn(parsed);
        when(aiAnalysisService.analyze("", "Java", log, parsed)).thenReturn(sampleAiResponse());

        mockAi("true", log, "").andExpect(status().isOk())
                .andExpect(jsonPath("$.summary").value("요청 처리 실패가 확인되었습니다."));

        verify(aiAnalysisService).analyze("", "Java", log, parsed);
        verify(analysisRepository).saveAiResultAndComplete(
                org.mockito.ArgumentMatchers.eq(7L), org.mockito.ArgumentMatchers.eq(USER_A_ID), any(String.class));
    }

    @Test
    void mockAnalysisFailureUsesNormalFailedStatusAndSafeErrorResponse() throws Exception {
        String marker = "[[DEVTRACE_MOCK_AI_FAILURE]]";
        when(aiAnalysisService.mockModeEnabled()).thenReturn(true);
        when(sensitiveDataMasker.mask(marker)).thenReturn(new SensitiveDataMasker.Result(marker, List.of()));
        when(javaLogParser.parse(marker)).thenReturn(new LogParseResponse(null, List.of(), List.of(), List.of(), List.of()));
        doThrow(new AiAnalysisException(AiAnalysisException.Failure.UPSTREAM))
                .when(aiAnalysisService).analyze("", "Java", marker, new LogParseResponse(null, List.of(), List.of(), List.of(), List.of()));

        mockAi("true", marker, "").andExpect(status().isBadGateway())
                .andExpect(content().string(not(containsString(marker))));

        verify(analysisRepository).markAiAnalysisFailed(7L, USER_A_ID);
        verify(analysisRepository, never()).saveAiResultAndComplete(anyLong(), anyLong(), any());
    }

    @Test
    void aiPreparationRemasksOnServerAndDoesNotExposeOrPersistSensitiveValues() throws Exception {
        String apiKey = "dummy-api-key-value";
        String originalLog = "password=dummy-original-password";
        String maskedLog = "password=[REDACTED]";
        when(sensitiveDataMasker.mask(originalLog)).thenReturn(new SensitiveDataMasker.Result(
                maskedLog, List.of(new SensitiveDataMasker.Detection(1, "PASSWORD", 1))));
        var parsed = new LogParseResponse(null, List.of(), List.of(), List.of(), List.of());
        when(javaLogParser.parse(maskedLog)).thenReturn(parsed);
        when(aiAnalysisService.analyze(apiKey, "Java", maskedLog, parsed)).thenReturn(sampleAiResponse());

        mockAi("true", originalLog, apiKey)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary").value("요청 처리 실패가 확인되었습니다."))
                .andExpect(jsonPath("$.possibleCauses[0]").value("Null 값이 전달되었을 가능성"))
                .andExpect(jsonPath("$.logEvidence[0].observedFact").value("NullPointerException이 기록됨"))
                .andExpect(jsonPath("$.logEvidence[0].interpretation").value("Null 참조가 원인일 수 있음"))
                .andExpect(jsonPath("$.solutions[0]").value("해당 입력을 검증하세요."))
                .andExpect(jsonPath("$.codeExample").value("if (value == null) { return; }"))
                .andExpect(jsonPath("$.verificationSteps[0]").value("관련 테스트를 실행하세요."))
                .andExpect(jsonPath("$.confidenceLevel").value("MEDIUM"))
                .andExpect(content().string(not(containsString(apiKey))))
                .andExpect(content().string(not(containsString("dummy-original-password"))))
                .andExpect(jsonPath("$.maskedLogPreview").doesNotExist());

        verify(sensitiveDataMasker).mask(originalLog);
        verify(javaLogParser).parse(maskedLog);
        verify(aiAnalysisService).analyze(apiKey, "Java", maskedLog, parsed);
        verify(analysisRepository).saveAiResultAndComplete(
                org.mockito.ArgumentMatchers.eq(7L), org.mockito.ArgumentMatchers.eq(USER_A_ID), any(String.class));
        verify(analysisRepository, never()).save(any(AnalysisRequest.class), anyLong());
    }

    @Test
    void blocksAiPreparationWhenMaskingFailsOrCannotBeVerified() throws Exception {
        String original = "password=dummy-sensitive-value";
        when(sensitiveDataMasker.mask(original)).thenThrow(new IllegalStateException("dummy-api-key-error"));
        mockAi("true", original, "dummy-api-key")
                .andExpect(status().isBadRequest())
                .andExpect(content().string(not(containsString("dummy-sensitive-value"))))
                .andExpect(content().string(not(containsString("dummy-api-key-error"))));

        when(sensitiveDataMasker.mask("unverifiable log")).thenReturn(null);
        mockAi("true", "unverifiable log", "dummy-api-key").andExpect(status().isBadRequest());
        verify(javaLogParser, never()).parse(any());
    }

    @Test
    void mapsAiTimeoutAndUpstreamFailuresWithoutExposingExceptionDetails() throws Exception {
        String log = "ERROR example failure";
        String apiKey = "dummy-api-key";
        String masked = "ERROR example failure";
        var parsed = new LogParseResponse(null, List.of(), List.of(), List.of(), List.of());
        when(sensitiveDataMasker.mask(log)).thenReturn(new SensitiveDataMasker.Result(masked, List.of()));
        when(javaLogParser.parse(masked)).thenReturn(parsed);
        doThrow(new AiAnalysisException(AiAnalysisException.Failure.TIMEOUT))
                .when(aiAnalysisService).analyze(apiKey, "Java", masked, parsed);

        mockAi("true", log, apiKey).andExpect(status().isGatewayTimeout())
                .andExpect(content().string(not(containsString(apiKey))));
        verify(analysisRepository).markAiAnalysisFailed(7L, USER_A_ID);
        doThrow(new AiAnalysisException(AiAnalysisException.Failure.UPSTREAM))
                .when(aiAnalysisService).analyze(apiKey, "Java", masked, parsed);
        mockAi("true", log, apiKey).andExpect(status().isBadGateway());
    }

    @Test
    void rejectsAiPreparationIfMaskingOutputStillContainsSubmittedApiKey() throws Exception {
        String apiKey = "dummy-api-key-value";
        when(sensitiveDataMasker.mask("ordinary log")).thenReturn(
                new SensitiveDataMasker.Result("ordinary log " + apiKey, List.of()));
        mockAi("true", "ordinary log", apiKey)
                .andExpect(status().isBadRequest())
                .andExpect(content().string(not(containsString(apiKey))));
        verify(javaLogParser, never()).parse(any());
    }

    @Test
    void doesNotAnalyzeOrPersistAnotherUsersAnalysis() throws Exception {
        authenticateAs(USER_B_ID);
        when(analysisRepository.findByIdAndUserId(7L, USER_B_ID)).thenReturn(java.util.Optional.empty());

        mockAi("true", "safe log", "dummy-api-key").andExpect(status().isNotFound());

        verify(aiAnalysisService, never()).analyze(any(), any(), any(), any());
        verify(analysisRepository, never()).saveAiResultAndComplete(anyLong(), anyLong(), any());
    }

    @Test
    void returnsFailureWhenResultCannotBeStoredAndMarksAnalysisFailed() throws Exception {
        String log = "ERROR example failure";
        String key = "dummy-api-key";
        var parsed = new LogParseResponse(null, List.of(), List.of(), List.of(), List.of());
        when(sensitiveDataMasker.mask(log)).thenReturn(new SensitiveDataMasker.Result(log, List.of()));
        when(javaLogParser.parse(log)).thenReturn(parsed);
        when(aiAnalysisService.analyze(key, "Java", log, parsed)).thenReturn(sampleAiResponse());
        when(analysisRepository.saveAiResultAndComplete(anyLong(), anyLong(), any()))
                .thenThrow(new DataAccessResourceFailureException("private storage detail"));

        mockAi("true", log, key).andExpect(status().isInternalServerError())
                .andExpect(content().string(not(containsString("private storage detail"))));

        verify(analysisRepository).markAiAnalysisFailed(7L, USER_A_ID);
    }
    @Test
    void acceptsJavaAndSavesTheReceivedMessage() throws Exception {
        String errorMessage = "NullPointerException at UserService.java:25";
        mockMvc.perform(post("/api/analyses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"language":"Java","errorMessage":"%s"}
                                """.formatted(errorMessage)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.language").value("Java"))
                .andExpect(jsonPath("$.data.receivedErrorMessage").value(errorMessage))
                .andExpect(jsonPath("$.data.characterCount").value(errorMessage.length()))
                .andExpect(jsonPath("$.data.analysisStatus").value("PENDING"));

        verify(analysisRepository).save(new AnalysisRequest("Java", errorMessage), USER_A_ID);
    }

    @Test
    void savesAsAuthenticatedUserAndIgnoresClientSuppliedUserId() throws Exception {
        String errorMessage = "A submitted error";
        mockMvc.perform(post("/api/analyses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"language":"Java","errorMessage":"%s","userId":%d}
                                """.formatted(errorMessage, USER_B_ID)))
                .andExpect(status().isOk());
        verify(analysisRepository).save(new AnalysisRequest("Java", errorMessage), USER_A_ID);

        authenticateAs(USER_B_ID);
        mockMvc.perform(post("/api/analyses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"language":"Java","errorMessage":"B submitted error","userId":%d}
                                """.formatted(USER_A_ID)))
                .andExpect(status().isOk());
        verify(analysisRepository).save(new AnalysisRequest("Java", "B submitted error"), USER_B_ID);
    }

    private void authenticateAs(long userId) {
        UserPrincipal principal = new UserPrincipal(new UserAccount(
                userId, "user" + userId + "@example.com", "hash", "Test", "USER"));
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.getAuthorities()));
        SecurityContextHolder.setContext(context);
    }

    @Test
    void acceptsJavaScript() throws Exception {
        postAnalysis("JavaScript", "TypeError: value is undefined")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.language").value("JavaScript"));
    }

    @Test
    void rejectsMissingErrorMessage() throws Exception {
        mockMvc.perform(post("/api/analyses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"language\":\"Java\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItem("errorMessage")));
    }

    @Test
    void rejectsEmptyErrorMessage() throws Exception {
        postAnalysis("Java", "")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItem("errorMessage")));
    }

    @Test
    void rejectsBlankErrorMessage() throws Exception {
        postAnalysis("Java", "   ")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItem("errorMessage")));
    }

    @Test
    void rejectsUnsupportedLanguage() throws Exception {
        postAnalysis("Ruby", "Some error")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItem("language")));
    }

    @Test
    void rejectsErrorMessageLongerThanTenThousandCharacters() throws Exception {
        postAnalysis("Java", "x".repeat(10_001))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItem("errorMessage")));
    }

    @Test
    void rejectsMalformedJsonWithBadRequest() throws Exception {
        mockMvc.perform(post("/api/analyses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{invalid json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void returnsSafeErrorWhenDatabaseSaveFails() throws Exception {
        doThrow(new DataAccessResourceFailureException("sensitive database connection detail"))
                .when(analysisRepository).save(any(AnalysisRequest.class), anyLong());

        mockMvc.perform(post("/api/analyses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"language\":\"Java\",\"errorMessage\":\"Some error\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(content().string(not(containsString("sensitive database connection detail"))));
    }

    @Test
    void keepsHelloEndpointAvailable() throws Exception {
        mockMvc.perform(get("/api/hello"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("DevTrace 백엔드 연결 성공!"));
    }

    private ResultActions postAnalysis(String language, String errorMessage) throws Exception {
        String escapedMessage = errorMessage.replace("\\", "\\\\").replace("\"", "\\\"");
        String content = """
                {"language":"%s","errorMessage":"%s"}
                """.formatted(language, escapedMessage);
        return mockMvc.perform(post("/api/analyses")
                .contentType(MediaType.APPLICATION_JSON)
                .content(content));
    }

    private ResultActions mockAi(String consent, String logText, String apiKey) throws Exception {
        String escapedLog = logText.replace("\\", "\\\\").replace("\"", "\\\"");
        String escapedKey = apiKey.replace("\\", "\\\\").replace("\"", "\\\"");
        return mockMvc.perform(post("/api/analyses/ai")
                .contentType(MediaType.APPLICATION_JSON)
                .content(("{\"consent\":%s,\"analysisId\":7,\"apiKey\":\"%s\",\"language\":\"Java\",\"logText\":\"%s\"}")
                        .formatted(consent, escapedKey, escapedLog)));
    }

    private AiAnalysisResponse sampleAiResponse() {
        return new AiAnalysisResponse(
                "요청 처리 실패가 확인되었습니다.",
                List.of("Null 값이 전달되었을 가능성"),
                List.of(new AiAnalysisResponse.LogEvidence("NullPointerException이 기록됨", "Null 참조가 원인일 수 있음")),
                List.of("해당 입력을 검증하세요."),
                "if (value == null) { return; }",
                List.of("관련 테스트를 실행하세요."),
                "MEDIUM");
    }
}






