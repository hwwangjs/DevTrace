import test from 'node:test';
import assert from 'node:assert/strict';
import { formatLogParseSummary } from '../src/analysisSummary.js';

test('formats only populated parser response fields as a readable summary', () => {
  const summary = formatLogParseSummary({
    representativeError: { lineNumber: 2, text: 'ERROR java.lang.IllegalStateException' },
    keyLogs: [{ lineNumber: 2, text: 'ERROR java.lang.IllegalStateException' }],
    relatedExceptions: [{ lineNumber: 5, text: 'Caused by: java.io.IOException' }],
    sourceLocations: [{
      logLineNumber: 3,
      fileName: 'Service.java',
      sourceLineNumber: 42,
      stackFrame: 'com.devtrace.Service.run',
    }],
    contextPreview: [{ lineNumber: 1, text: 'INFO starting' }],
  });

  assert.match(summary, /대표 오류 및 오류 유형\nL2: ERROR java\.lang\.IllegalStateException/);
  assert.match(summary, /핵심 로그\nL2: ERROR java\.lang\.IllegalStateException/);
  assert.match(summary, /관련 예외\nL5: Caused by: java\.io\.IOException/);
  assert.match(summary, /Service\.java:42/);
  assert.match(summary, /주변 로그\nL1: INFO starting/);
  assert.doesNotMatch(summary, /maskedLogPreview|logText|원본 로그/);
});

test('omits parser sections that are missing or empty', () => {
  assert.equal(formatLogParseSummary({
    representativeError: null,
    keyLogs: [],
    relatedExceptions: [],
    sourceLocations: [],
    contextPreview: [],
    maskedLogPreview: 'do not copy this preview',
  }), '');
  assert.equal(formatLogParseSummary(null), '');
});
