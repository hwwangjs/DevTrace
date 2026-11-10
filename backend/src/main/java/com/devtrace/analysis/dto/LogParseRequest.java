package com.devtrace.analysis.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;



/** 서버에서 파싱하고 마스킹할 원본 로그 입력을 전달한다. */
public record LogParseRequest(
        @NotBlank(message = "로그를 입력해 주세요.")
        @Size(max = 50_000, message = "로그는 50,000자 이하여야 합니다.")
        String logText) {

    
/** 디버그 문자열에 원본 로그가 노출되지 않도록 입력값을 생략한다. */
    @Override
    public String toString() {
        return "LogParseRequest[logText=<omitted>]";
    }
}
