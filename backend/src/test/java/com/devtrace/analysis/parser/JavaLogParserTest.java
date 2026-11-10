package com.devtrace.analysis.parser;

import com.devtrace.analysis.dto.LogParseResponse;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;


/** Java 로그에서 대표 오류, 예외, 스택 위치와 주변 문맥을 추출하는 동작을 검증한다. */
class JavaLogParserTest {
    private final JavaLogParser parser = new JavaLogParser();

    @Test
    void extractsRepresentativeErrorCausesStackLocationsAndContext() {
        var result = parser.parse("""
                2026-10-30 10:00:00 INFO starting
                2026-10-30 10:00:01 ERROR request failed: java.lang.IllegalStateException
                java.lang.IllegalStateException: operation failed
                    at com.devtrace.UserService.run(UserService.java:42)
                Caused by: java.io.IOException: file unavailable
                    at com.devtrace.FileReader.read(FileReader.java:19)
                """);

        assertThat(result.representativeError().lineNumber()).isEqualTo(2);
        assertThat(result.representativeError().text()).contains("ERROR", "IllegalStateException");
        assertThat(result.relatedExceptions()).hasSize(1);
        assertThat(result.relatedExceptions().getFirst().text()).contains("Caused by: java.io.IOException");
        assertThat(result.sourceLocations()).extracting(LogParseResponse.SourceLocation::fileName)
                .containsExactly("UserService.java", "FileReader.java");
        assertThat(result.sourceLocations()).extracting(LogParseResponse.SourceLocation::logLineNumber)
                .containsExactly(4, 6);
        assertThat(result.sourceLocations()).extracting(LogParseResponse.SourceLocation::sourceLineNumber)
                .containsExactly(42, 19);
        assertThat(result.keyLogs()).extracting(LogParseResponse.LogLine::text)
                .anyMatch(line -> line.contains("ERROR"))
                .anyMatch(line -> line.contains("Caused by:"));
        assertThat(result.contextPreview()).extracting(LogParseResponse.LogLine::lineNumber)
                .contains(1, 2, 3, 4, 5, 6);
    }

    @Test
    void returnsNoRepresentativeErrorForUnrecognizedLog() {
        var result = parser.parse("INFO application started");
        assertThat(result.representativeError()).isNull();
        assertThat(result.relatedExceptions()).isEmpty();
        assertThat(result.keyLogs()).isEmpty();
        assertThat(result.sourceLocations()).isEmpty();
    }
}
