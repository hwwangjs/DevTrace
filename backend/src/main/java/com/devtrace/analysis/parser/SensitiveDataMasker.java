package com.devtrace.analysis.parser;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;


@Component

/** 로그의 비밀번호·토큰·키 등 흔한 민감정보 패턴을 탐지하고 값을 가린다. */
public class SensitiveDataMasker {
    private static final String REDACTED = "[REDACTED]";
    private static final String ASSIGNMENT_VALUE = "(?:\"([^\"]*)\"|'([^']*)'|([^\\s,;}\\]]+))";
    private static final List<Rule> RULES = List.of(
            new Rule("PASSWORD", Pattern.compile("(?i)(\\b(?:password|passwd|pwd|passphrase)\\b[\"']?\\s*[:=]\\s*)" + ASSIGNMENT_VALUE),
                    SensitiveDataMasker::redactAssignment),
            new Rule("API_KEY", Pattern.compile("(?i)(\\b(?:api[_-]?key|access[_-]?key|secret[_-]?key|client[_-]?secret)\\b[\"']?\\s*[:=]\\s*)" + ASSIGNMENT_VALUE),
                    SensitiveDataMasker::redactAssignment),
            new Rule("ACCESS_TOKEN", Pattern.compile("(?i)(\\b(?:access[_-]?token|refresh[_-]?token|id[_-]?token|auth(?:orization)?|token)\\b[\"']?\\s*[:=]\\s*)(?:Bearer\\s+)?" + ASSIGNMENT_VALUE),
                    SensitiveDataMasker::redactAssignment),
            new Rule("CONNECTION_CREDENTIAL", Pattern.compile("(:/{2}[^:/\\s]+:)([^@/\\s]+)(@)"),
                    matcher -> matcher.group(1) + REDACTED + matcher.group(3)),
            new Rule("ACCESS_TOKEN", Pattern.compile("(?i)(\\bBearer\\s+)[A-Za-z0-9._~+/-]+=*"),
                    matcher -> matcher.group(1) + REDACTED),
            new Rule("API_KEY", Pattern.compile("\\b(?:sk-(?:proj-|live-|test-)?[A-Za-z0-9_-]{16,}|gh[pousr]_[A-Za-z0-9]{20,}|AKIA[A-Z0-9]{16}|AIza[0-9A-Za-z_-]{35})\\b"),
                    matcher -> REDACTED),
            new Rule("ACCESS_TOKEN", Pattern.compile("\\beyJ[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}\\b"),
                    matcher -> REDACTED),
            new Rule("PRIVATE_KEY", Pattern.compile("(?s)(-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----).*?(-----END (?:RSA |EC |OPENSSH )?PRIVATE KEY-----)"),
                    matcher -> "[REDACTED PRIVATE KEY]" + matcher.group().replaceAll("[^\\r\\n]", "")));

    
/** 로그의 민감정보를 치환하고 종류·줄 번호별 탐지 결과를 반환한다. */
    public Result mask(String originalLog) {
        String maskedLog = originalLog;
        Map<DetectionKey, Integer> counts = new LinkedHashMap<>();
        for (Rule rule : RULES) {
            Matcher matcher = rule.pattern().matcher(maskedLog);
            StringBuffer output = new StringBuffer();
            while (matcher.find()) {
                int lineNumber = lineNumberAt(maskedLog, matcher.start());
                counts.merge(new DetectionKey(lineNumber, rule.type()), 1, Integer::sum);
                matcher.appendReplacement(output, Matcher.quoteReplacement(rule.replacement().apply(matcher)));
            }
            matcher.appendTail(output);
            maskedLog = output.toString();
        }
        List<Detection> detections = counts.entrySet().stream()
                .map(entry -> new Detection(entry.getKey().lineNumber(), entry.getKey().type(), entry.getValue()))
                .toList();
        return new Result(maskedLog, detections);
    }

    private static String redactAssignment(Matcher matcher) {
        if (matcher.group(2) != null) return matcher.group(1) + "\"" + REDACTED + "\"";
        if (matcher.group(3) != null) return matcher.group(1) + "'" + REDACTED + "'";
        return matcher.group(1) + REDACTED;
    }

    private int lineNumberAt(String text, int offset) {
        int lineNumber = 1;
        for (int i = 0; i < offset; i++) {
            char current = text.charAt(i);
            if (current == '\r') {
                lineNumber++;
                if (i + 1 < offset && text.charAt(i + 1) == '\n') i++;
            } else if (current == '\n') {
                lineNumber++;
            }
        }
        return lineNumber;
    }

    
    /** 민감정보 유형별 탐지 패턴과 치환 동작을 보관한다. */
    private record Rule(String type, Pattern pattern, Function<Matcher, String> replacement) {
    }

    
    /** 탐지 결과를 로그 줄과 민감정보 유형으로 묶기 위한 내부 키다. */
    private record DetectionKey(int lineNumber, String type) {
    }

    
    /** 민감정보가 발견된 줄, 종류 및 횟수를 표현한다. */
    public record Detection(int lineNumber, String type, int occurrences) {
    }

    
    /** 민감정보를 가린 로그와 탐지 내역을 함께 전달한다. */
    public record Result(String maskedLog, List<Detection> detections) {
    }
}
