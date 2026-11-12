package com.devtrace.analysis.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AnalysisResolutionNoteRequest(
        @Size(max = 20_000) String rootCause,
        @Size(max = 50_000) String attemptedSteps,
        @Size(max = 20_000) String finalSolution,
        @Size(max = 20_000) String testResult,
        @NotNull ResolutionStatus resolutionStatus) {
}
