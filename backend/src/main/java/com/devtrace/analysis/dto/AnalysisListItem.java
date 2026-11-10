package com.devtrace.analysis.dto;

import java.time.OffsetDateTime;



/** 분석 목록에서 한 건의 식별 정보와 상태를 전달한다. */
public record AnalysisListItem(
        long id,
        String language,
        String errorMessage,
        String analysisStatus,
        OffsetDateTime createdAt) {

    @Override
    public String toString() {
        return "AnalysisListItem[id=" + id + ", language=" + language + ", errorMessage=<omitted>, analysisStatus="
                + analysisStatus + ", createdAt=" + createdAt + "]";
    }
}
