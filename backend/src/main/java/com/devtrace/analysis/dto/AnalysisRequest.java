package com.devtrace.analysis.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;



/** 분석 등록 요청의 언어와 오류 메시지를 담고 입력값을 검증한다. */
public record AnalysisRequest(
        @NotBlank(message = "프로그래밍 언어를 선택해 주세요.")
        @Pattern(
                regexp = "^(Java|JavaScript|TypeScript|Python|SQL|Other)$",
                message = "허용된 프로그래밍 언어 중 하나를 선택해 주세요.")
        String language,
        @NotBlank(message = "오류 메시지는 필수 입력값입니다.")
        @Size(max = 10_000, message = "오류 메시지는 10,000자 이하여야 합니다.")
        String errorMessage) {

    @Override
    public String toString() {
        return "AnalysisRequest[language=" + language + ", errorMessage=<omitted>]";
    }
}
