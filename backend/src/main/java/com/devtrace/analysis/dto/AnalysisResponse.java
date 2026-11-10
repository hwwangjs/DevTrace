package com.devtrace.analysis.dto;



/** 새 분석 요청의 접수 결과와 현재 분석 상태를 표현한다. */
public record AnalysisResponse(boolean success, String message, Data data) {
    
    /** 분석 접수 응답에 포함되는 언어, 메시지 길이 및 상태 정보를 표현한다. */
    public record Data(String language, String receivedErrorMessage, int characterCount, String analysisStatus) {
        @Override
        public String toString() {
            return "Data[language=" + language + ", receivedErrorMessage=<omitted>, characterCount="
                    + characterCount + ", analysisStatus=" + analysisStatus + "]";
        }
    }
}
