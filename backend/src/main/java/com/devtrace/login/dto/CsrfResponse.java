package com.devtrace.login.dto;


/** 클라이언트가 상태 변경 요청에 사용할 CSRF 헤더 이름과 토큰을 전달한다. */
public record CsrfResponse(String headerName, String token) {
    @Override
    public String toString() {
        return "CsrfResponse[headerName=" + headerName + ", token=<omitted>]";
    }
}
