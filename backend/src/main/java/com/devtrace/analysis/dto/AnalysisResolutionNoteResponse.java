package com.devtrace.analysis.dto;

import java.time.OffsetDateTime;

public record AnalysisResolutionNoteResponse(
        long analysisRequestId,
        String rootCause,
        String attemptedSteps,
        String finalSolution,
        String testResult,
        ResolutionStatus resolutionStatus,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {
}
