function formatLogLine(line) {
  if (!line || typeof line.text !== 'string' || !line.text.trim()) return null;
  return line.lineNumber == null ? line.text : `L${line.lineNumber}: ${line.text}`;
}

function formatLines(lines) {
  return (Array.isArray(lines) ? lines : []).map(formatLogLine).filter(Boolean);
}

export function formatLogParseSummary(result) {
  if (!result) return '';

  const sections = [];
  const representative = formatLogLine(result.representativeError);
  if (representative) sections.push(`대표 오류 및 오류 유형\n${representative}`);

  const keyLogs = formatLines(result.keyLogs);
  if (keyLogs.length) sections.push(`핵심 로그\n${keyLogs.join('\n')}`);

  const relatedExceptions = formatLines(result.relatedExceptions);
  if (relatedExceptions.length) sections.push(`관련 예외\n${relatedExceptions.join('\n')}`);

  const sourceLocations = (Array.isArray(result.sourceLocations) ? result.sourceLocations : [])
    .map((location) => {
      if (!location) return null;
      const locationText = [
        typeof location.stackFrame === 'string' && location.stackFrame.trim() ? location.stackFrame : null,
        typeof location.fileName === 'string' && location.fileName.trim() && location.sourceLineNumber != null
          ? `${location.fileName}:${location.sourceLineNumber}`
          : null,
      ].filter(Boolean).join(' — ');
      if (!locationText) return null;
      return location.logLineNumber == null ? locationText : `로그 L${location.logLineNumber}: ${locationText}`;
    })
    .filter(Boolean);
  if (sourceLocations.length) sections.push(`파일 및 라인 정보\n${sourceLocations.join('\n')}`);

  const context = formatLines(result.contextPreview);
  if (context.length) sections.push(`주변 로그\n${context.join('\n')}`);

  return sections.join('\n\n');
}
