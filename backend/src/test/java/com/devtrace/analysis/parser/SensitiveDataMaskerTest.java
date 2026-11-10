package com.devtrace.analysis.parser;

import com.devtrace.analysis.dto.*;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;


/** 민감정보 탐지·마스킹과 원문 비노출 동작을 검증한다. */
class SensitiveDataMaskerTest {
    private final SensitiveDataMasker masker = new SensitiveDataMasker();

    @Test
    void masksCommonCredentialsWithoutReturningTheirValues() {
        String password = "dummy-password-value";
        String apiKey = "sk-test-abcdefghijklmnop123456";
        String accessToken = "dummy-access-token-value";
        String jwt = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJkZW1vIn0.dummySignatureValue123";
        String privateKeyBody = "dummy-private-key-body";
        String original = """
                {"password":"%s"}
                api_key=%s
                Authorization: Bearer %s
                jwt=%s
                jdbc:postgresql://devtrace:dummy-db-password@localhost:5432/devtrace
                -----BEGIN PRIVATE KEY-----
                %s
                -----END PRIVATE KEY-----
                """.formatted(password, apiKey, accessToken, jwt, privateKeyBody);

        var result = masker.mask(original);

        assertThat(result.maskedLog()).contains("[REDACTED]").contains("[REDACTED PRIVATE KEY]");
        assertThat(result.maskedLog()).doesNotContain(password, apiKey, accessToken, jwt, privateKeyBody, "dummy-db-password");
        assertThat(result.detections()).extracting(SensitiveDataMasker.Detection::type)
                .contains("PASSWORD", "API_KEY", "ACCESS_TOKEN", "CONNECTION_CREDENTIAL", "PRIVATE_KEY");
        assertThat(result.detections()).extracting(SensitiveDataMasker.Detection::lineNumber)
                .contains(1, 2, 3, 4, 5);
    }

    @Test
    void requestToStringDoesNotRevealRawLogText() {
        String dummySecret = "dummy-secret-for-toString";
        var request = new LogParseRequest("password=" + dummySecret);

        assertThat(request.toString()).contains("<omitted>").doesNotContain(dummySecret);
        var aiRequest = new AiAnalysisRequest(true, 1L, dummySecret, "Java", "log=" + dummySecret);
        assertThat(aiRequest.toString()).contains("<omitted>").doesNotContain(dummySecret);
        assertThat(aiRequest.toString()).doesNotContain("log=" + dummySecret);
        assertThat(new AiAnalysisResponse("dummy-original-log-value", List.of(), List.of(), List.of(), "",
                List.of(), "LOW").toString()).doesNotContain("dummy-original-log-value");
        assertThat(new AnalysisRequest("Java", dummySecret).toString()).doesNotContain(dummySecret);
        var response = new AnalysisResponse(true, "ok",
                new AnalysisResponse.Data("Java", dummySecret, dummySecret.length(), "PENDING"));
        assertThat(response.toString()).doesNotContain(dummySecret);
        var listItem = new AnalysisListItem(1, "Java", dummySecret, "PENDING", java.time.OffsetDateTime.now());
        assertThat(listItem.toString()).doesNotContain(dummySecret);
    }
}

