package com.devtrace.analysis.dto;

import java.time.OffsetDateTime;



/** 분석 상세 정보와 저장된 AI 결과가 있을 경우 그 결과를 전달한다. */
public record AnalysisDetailResponse(
        long id,
        String language,
        String errorMessage,
        String analysisStatus,
        OffsetDateTime createdAt,
        OffsetDateTime completedAt,
        AiAnalysisResponse aiAnalysisResult) {

    @Override
    public String toString() {
        return "AnalysisDetailResponse[id=" + id + ", language=" + language
                + ", errorMessage=<omitted>, analysisStatus=" + analysisStatus
                + ", createdAt=" + createdAt + ", completedAt=" + completedAt
                + ", aiAnalysisResult=<omitted>]";
    }
}
