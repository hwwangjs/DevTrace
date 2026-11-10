package com.devtrace.analysis.dto;

import com.devtrace.analysis.parser.SensitiveDataMasker;
import java.util.List;



/** 파싱 신호와 마스킹된 로그 미리보기 및 민감정보 탐지 결과를 전달한다. */
public record LogParseResponse(
        LogLine representativeError,
        List<LogLine> relatedExceptions,
        List<LogLine> keyLogs,
        List<SourceLocation> sourceLocations,
        List<LogLine> contextPreview,
        String maskedLogPreview,
        List<SensitiveDataMasker.Detection> sensitiveDetections) {
    public LogParseResponse(
            LogLine representativeError,
            List<LogLine> relatedExceptions,
            List<LogLine> keyLogs,
            List<SourceLocation> sourceLocations,
            List<LogLine> contextPreview) {
        this(representativeError, relatedExceptions, keyLogs, sourceLocations, contextPreview, "", List.of());
    }

    
    /** 로그에서 추출한 줄 번호와 해당 줄의 텍스트를 표현한다. */
    public record LogLine(int lineNumber, String text) {
    }

    
    /** 로그에 나타난 소스 파일, 코드 줄, 스택 프레임 위치를 표현한다. */
    public record SourceLocation(int logLineNumber, String fileName, int sourceLineNumber, String stackFrame) {
    }
}
