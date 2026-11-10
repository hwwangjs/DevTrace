package com.devtrace.analysis.dto;

import java.util.List;



/** 오류 요약, 원인 추정, 로그 근거, 해결·검증 정보를 구조화해 표현한다. */
public record AiAnalysisResponse(
        String summary,
        List<String> possibleCauses,
        List<LogEvidence> logEvidence,
        List<String> solutions,
        String codeExample,
        List<String> verificationSteps,
        String confidenceLevel) {

    @Override
    public String toString() {
        return "AiAnalysisResponse[analysis=<omitted>]";
    }

    
    /** 로그에서 직접 확인된 사실과 그 사실에 대한 해석을 구분해 표현한다. */
    public record LogEvidence(String observedFact, String interpretation) {
    }
}
