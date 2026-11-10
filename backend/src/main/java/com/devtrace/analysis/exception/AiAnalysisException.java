package com.devtrace.analysis.exception;



/** 요청 시간 초과, 상위 서비스 실패, 잘못된 응답을 안전한 유형으로 전달한다. */
public class AiAnalysisException extends RuntimeException {
    
    /** AI 분석 실패를 시간 초과, 상위 서비스 오류, 응답 형식 오류로 구분한다. */
    public enum Failure { TIMEOUT, UPSTREAM, INVALID_RESPONSE }

    private final Failure failure;

    public AiAnalysisException(Failure failure) {
        super("AI analysis failed");
        this.failure = failure;
    }

    public Failure failure() {
        return failure;
    }
}
