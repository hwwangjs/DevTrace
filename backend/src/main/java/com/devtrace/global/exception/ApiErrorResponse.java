package com.devtrace.global.exception;

import java.util.List;



/** API 처리 결과의 성공 여부, 메시지 및 입력 오류 목록을 표현한다. */
public record ApiErrorResponse(boolean success, String message, List<FieldError> errors) {
    
    /** 요청 필드 하나의 이름과 검증 실패 사유를 표현한다. */
    public record FieldError(String field, String reason) {
    }
}