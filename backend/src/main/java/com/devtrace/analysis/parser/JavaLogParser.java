package com.devtrace.analysis.parser;

import com.devtrace.analysis.dto.LogParseResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;


@Component

/** Java 로그에서 오류·예외·원인 연결 및 스택 위치를 추출하며 원인을 단정하지 않는다. */
public class JavaLogParser {
    private static final int MAX_KEY_LOGS = 100;
    private static final int MAX_EXCEPTIONS = 50;
    private static final int MAX_LOCATIONS = 100;
    private static final int MAX_CONTEXT_LINES = 40;
    private static final Pattern ERROR_MARKER = Pattern.compile("\\bERROR\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern EXCEPTION_MARKER = Pattern.compile("\\b[A-Za-z_$][\\w$]*(?:Exception|Error)\\b");
    private static final Pattern CAUSED_BY = Pattern.compile("^\\s*Caused by:\\s*(.+)$");
    private static final Pattern STACK_FRAME = Pattern.compile("^\\s*at\\s+(.+?)\\(([^():]+\\.java):(\\d+)\\)\\s*$");

    
/** 로그 줄을 검사해 대표 오류, 관련 예외, 스택 위치와 문맥을 추출한다. */
    public LogParseResponse parse(String logText) {
        String[] lines = logText.split("\\R", -1);
        List<Integer> relevant = new ArrayList<>();
        List<LogParseResponse.LogLine> related = new ArrayList<>();
        List<LogParseResponse.SourceLocation> locations = new ArrayList<>();
        int representativeIndex = -1;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            Matcher causedBy = CAUSED_BY.matcher(line);
            Matcher stackFrame = STACK_FRAME.matcher(line);
            boolean isError = ERROR_MARKER.matcher(line).find();
            boolean isException = EXCEPTION_MARKER.matcher(line).find();

            if (representativeIndex < 0 && (isError || isException)) {
                representativeIndex = i;
            }
            if (isError || isException || causedBy.find() || stackFrame.matches()) {
                relevant.add(i);
            }
            if (causedBy.reset().find() && related.size() < MAX_EXCEPTIONS) {
                related.add(new LogParseResponse.LogLine(i + 1, line.trim()));
            }
            if (stackFrame.reset().matches() && locations.size() < MAX_LOCATIONS) {
                locations.add(new LogParseResponse.SourceLocation(
                        i + 1, stackFrame.group(2), Integer.parseInt(stackFrame.group(3)), stackFrame.group(1).trim()));
            }
        }

        if (representativeIndex < 0) {
            for (int i = 0; i < lines.length; i++) {
                if (CAUSED_BY.matcher(lines[i]).find()) {
                    representativeIndex = i;
                    break;
                }
            }
        }
        LogParseResponse.LogLine representative = representativeIndex < 0
                ? null
                : new LogParseResponse.LogLine(representativeIndex + 1, lines[representativeIndex].trim());

        List<LogParseResponse.LogLine> keyLogs = relevant.stream()
                .limit(MAX_KEY_LOGS)
                .map(index -> new LogParseResponse.LogLine(index + 1, lines[index]))
                .toList();
        TreeSet<Integer> contextIndexes = new TreeSet<>();
        for (int index : relevant) {
            if (contextIndexes.size() >= MAX_CONTEXT_LINES) break;
            if (index > 0) contextIndexes.add(index - 1);
            contextIndexes.add(index);
            if (index + 1 < lines.length) contextIndexes.add(index + 1);
        }
        List<LogParseResponse.LogLine> context = contextIndexes.stream()
                .limit(MAX_CONTEXT_LINES)
                .map(index -> new LogParseResponse.LogLine(index + 1, lines[index]))
                .toList();

        return new LogParseResponse(representative, List.copyOf(related), keyLogs,
                List.copyOf(locations), context);
    }
}

