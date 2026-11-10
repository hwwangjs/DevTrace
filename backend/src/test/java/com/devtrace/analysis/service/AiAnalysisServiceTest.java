package com.devtrace.analysis.service;

import com.devtrace.analysis.dto.*;
import com.devtrace.analysis.exception.AiAnalysisException;
import com.devtrace.analysis.parser.SensitiveDataMasker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.client.RestClient;


/** 모의 및 외부 AI 분석의 구조화 응답, 실패 처리와 전송 내용을 검증한다. */
class AiAnalysisServiceTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SensitiveDataMasker masker = new SensitiveDataMasker();

    @Test
    void sendsOnlyMaskedLogAndReturnsStructuredResult() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        var resultBody = responseBody(sampleResponse());
        withServer(exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            respond(exchange, 200, resultBody);
        }, port -> {
            var service = service(port);
            String apiKey = "dummy-request-api-key";
            String originalLog = "password=dummy-original-log-value";
            String maskedLog = masker.mask(originalLog).maskedLog();
            var parserResult = new LogParseResponse(null, List.of(), List.of(), List.of(), List.of());

            AiAnalysisResponse response = service.analyze(apiKey, "Java", maskedLog, parserResult);

            assertThat(response.summary()).isEqualTo("요청 처리 실패가 확인되었습니다.");
            assertThat(response.logEvidence()).containsExactly(new AiAnalysisResponse.LogEvidence(
                    "NullPointerException이 기록됨", "Null 참조가 원인일 수 있음"));
            assertThat(authorization.get()).isEqualTo("Bearer " + apiKey);
            assertThat(requestBody.get()).contains(maskedLog, "\"store\":false", "\"model\":\"gpt-6-luna\"")
                    .doesNotContain(apiKey, "dummy-original-log-value");
        });
    }

    @Test
    void rejectsIncompleteOrMalformedResponses() throws Exception {
        var incomplete = objectMapper.createObjectNode().put("status", "incomplete");
        withServer(exchange -> respond(exchange, 200, objectMapper.writeValueAsString(incomplete)), port -> {
            var service = service(port);
            assertThatThrownBy(() -> service.analyze("dummy-api-key", "Java", "safe masked text",
                    new LogParseResponse(null, List.of(), List.of(), List.of(), List.of())))
                    .isInstanceOf(AiAnalysisException.class)
                    .extracting(error -> ((AiAnalysisException) error).failure())
                    .isEqualTo(AiAnalysisException.Failure.INVALID_RESPONSE);
        });

        ObjectNode malformed = objectMapper.createObjectNode().put("status", "completed");
        malformed.putArray("output").addObject().putArray("content").addObject()
                .put("type", "output_text").put("text", "not-json");
        withServer(exchange -> respond(exchange, 200, objectMapper.writeValueAsString(malformed)), port -> {
            var service = service(port);
            assertThatThrownBy(() -> service.analyze("dummy-api-key", "Java", "safe masked text",
                    new LogParseResponse(null, List.of(), List.of(), List.of(), List.of())))
                    .isInstanceOf(AiAnalysisException.class)
                    .extracting(error -> ((AiAnalysisException) error).failure())
                    .isEqualTo(AiAnalysisException.Failure.INVALID_RESPONSE);
        });
    }

    @Test
    void classifiesOpenAiRequestTimeoutWithoutLeakingRequestDetails() throws Exception {
        String successBody = responseBody(sampleResponse());
        withServer(exchange -> {
            try {
                Thread.sleep(1_500);
                respond(exchange, 200, successBody);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } catch (IOException ignored) {
// 클라이언트 시간 초과로 연결이 이미 종료된 상태다.
            }
        }, port -> {
            var service = new AiAnalysisService(RestClient.builder(), objectMapper, masker,
                    "http://127.0.0.1:" + port + "/v1/responses", "gpt-6-luna", 1);
            assertThatThrownBy(() -> service.analyze("dummy-api-key", "Java", "masked log",
                    new LogParseResponse(null, List.of(), List.of(), List.of(), List.of())))
                    .isInstanceOf(AiAnalysisException.class)
                    .extracting(error -> ((AiAnalysisException) error).failure())
                    .isEqualTo(AiAnalysisException.Failure.TIMEOUT);
        });
    }

    @Test
    void developmentMockReturnsStructuredLogSpecificResultWithoutApiKeyOrNetworkCall() {
        var service = mockService("dev");
        String maskedLog = "java.lang.NullPointerException: value was null\n at Example.run(Example.java:12)";
        var parserResult = new LogParseResponse(
                new LogParseResponse.LogLine(1, "java.lang.NullPointerException: value was null"),
                List.of(), List.of(), List.of(), List.of());

        AiAnalysisResponse result = service.analyze(null, "Java", maskedLog, parserResult);

        assertThat(service.mockModeEnabled()).isTrue();
        assertThat(result.summary()).contains("Null");
        assertThat(result.logEvidence()).hasSize(1);
        assertThat(result.possibleCauses()).isNotEmpty();
        assertThat(result.solutions()).isNotEmpty();
        assertThat(result.codeExample()).isNotBlank();
        assertThat(result.verificationSteps()).isNotEmpty();
        assertThat(result.confidenceLevel()).isEqualTo("MEDIUM");
    }

    @Test
    void mockFailureMarkerProducesTheSameSafeAnalysisFailure() {
        var service = mockService("dev");
        assertThatThrownBy(() -> service.analyze(null, "Java", AiAnalysisService.MOCK_FAILURE_MARKER,
                new LogParseResponse(null, List.of(), List.of(), List.of(), List.of())))
                .isInstanceOf(AiAnalysisException.class)
                .extracting(error -> ((AiAnalysisException) error).failure())
                .isEqualTo(AiAnalysisException.Failure.UPSTREAM);
    }

    @Test
    void mockModeCannotBeEnabledInProductionProfiles() {
        var service = mockService("dev", "production");

        assertThat(service.mockModeEnabled()).isFalse();
    }

    private AiAnalysisService service(int port) {
        return new AiAnalysisService(RestClient.builder(), objectMapper, masker,
                "http://127.0.0.1:" + port + "/v1/responses", "gpt-6-luna", 5);
    }

    private AiAnalysisService mockService(String... profiles) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profiles);
        return new AiAnalysisService(RestClient.builder(), objectMapper, masker,
                environment, true,
                "http://127.0.0.1:1/never-called", "gpt-6-luna", 1);
    }

    private AiAnalysisResponse sampleResponse() {
        return new AiAnalysisResponse(
                "요청 처리 실패가 확인되었습니다.",
                List.of("Null 값이 전달되었을 가능성"),
                List.of(new AiAnalysisResponse.LogEvidence("NullPointerException이 기록됨", "Null 참조가 원인일 수 있음")),
                List.of("해당 입력을 검증하세요."),
                "if (value == null) { return; }",
                List.of("관련 테스트를 실행하세요."),
                "MEDIUM");
    }

    private String responseBody(AiAnalysisResponse result) throws Exception {
        ObjectNode envelope = objectMapper.createObjectNode().put("status", "completed");
        ArrayNode output = envelope.putArray("output");
        ObjectNode message = output.addObject();
        ArrayNode content = message.putArray("content");
        content.addObject().put("type", "output_text").put("text", objectMapper.writeValueAsString(result));
        return objectMapper.writeValueAsString(envelope);
    }

    private void withServer(Handler handler, PortAction action) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/responses", handler::handle);
        server.start();
        try {
            action.run(server.getAddress().getPort());
        } finally {
            server.stop(0);
        }
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    @FunctionalInterface
    
    /** AI 서비스 테스트용 HTTP 서버 요청 처리를 표현하는 함수형 인터페이스다. */
    private interface Handler {
        void handle(HttpExchange exchange) throws IOException;
    }

    @FunctionalInterface
    
    /** AI 서비스 테스트 서버의 포트를 받아 검증 동작을 수행하는 함수형 인터페이스다. */
    private interface PortAction {
        void run(int port) throws Exception;
    }
}
