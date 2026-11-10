package com.devtrace.analysis.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;



/** 동의 여부, 분석 대상, 요청 범위에서만 사용하는 키와 로그를 전달한다. */
public record AiAnalysisRequest(
        @NotNull Boolean consent,
        @NotNull @Positive Long analysisId,
        @Size(max = 4_096) String apiKey,
        @NotBlank @Pattern(regexp = "^(Java|JavaScript|TypeScript|Python|SQL|Other)$") String language,
        @NotBlank @Size(max = 50_000) String logText) {

    @Override
    public String toString() {
        return "AiAnalysisRequest[consent=" + consent + ", analysisId=" + analysisId
                + ", apiKey=<omitted>, language=" + language
                + ", logText=<omitted>]";
    }
}
