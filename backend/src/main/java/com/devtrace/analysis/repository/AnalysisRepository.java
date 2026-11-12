package com.devtrace.analysis.repository;

import com.devtrace.analysis.dto.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository

/** JdbcTemplate으로 분석 요청과 AI 결과를 저장·조회하며 모든 데이터 접근을 사용자 소유 범위로 제한한다. */
public class AnalysisRepository {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public AnalysisRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    
/** 인증 사용자 ID와 함께 분석 요청을 저장한다. */
    public void save(AnalysisRequest request, long userId) {
        jdbcTemplate.update("""
                INSERT INTO analysis_requests (language, error_message, analysis_status, user_id)
                VALUES (?, ?, ?, ?)
                """, request.language(), request.errorMessage(), "PENDING", userId);
    }

    
/** 사용자 ID 조건을 적용해 해당 사용자의 분석 목록을 조회한다. */
    public List<AnalysisListItem> findAll(long userId) {
        return jdbcTemplate.query("""
                SELECT id, language, error_message, analysis_status, created_at
                FROM analysis_requests
                WHERE user_id = ?
                ORDER BY created_at DESC
                """, (resultSet, rowNumber) -> new AnalysisListItem(
                resultSet.getLong("id"),
                resultSet.getString("language"),
                resultSet.getString("error_message"),
                resultSet.getString("analysis_status"),
                resultSet.getObject("created_at", OffsetDateTime.class)), userId);
    }

    
/** 분석 ID와 소유자 ID가 모두 일치하는 분석을 조회한다. */
    public Optional<AnalysisListItem> findByIdAndUserId(long id, long userId) {
        List<AnalysisListItem> matches = jdbcTemplate.query("""
                SELECT id, language, error_message, analysis_status, created_at
                FROM analysis_requests
                WHERE id = ? AND user_id = ?
                """, (resultSet, rowNumber) -> new AnalysisListItem(
                resultSet.getLong("id"),
                resultSet.getString("language"),
                resultSet.getString("error_message"),
                resultSet.getString("analysis_status"),
                resultSet.getObject("created_at", OffsetDateTime.class)), id, userId);
        return matches.stream().findFirst();
    }

    public Optional<AnalysisResolutionNoteResponse> findResolutionNoteByIdAndUserId(long id, long userId) {
        return jdbcTemplate.query("""
                SELECT a.id AS analysis_request_id,
                       COALESCE(n.root_cause, '') AS root_cause,
                       COALESCE(n.attempted_steps, '') AS attempted_steps,
                       COALESCE(n.final_solution, '') AS final_solution,
                       COALESCE(n.test_result, '') AS test_result,
                       COALESCE(n.resolution_status, 'UNRESOLVED') AS resolution_status,
                       n.created_at,
                       n.updated_at
                FROM analysis_requests a
                LEFT JOIN analysis_resolution_notes n ON n.analysis_request_id = a.id
                WHERE a.id = ? AND a.user_id = ?
                """, (resultSet, rowNumber) -> new AnalysisResolutionNoteResponse(
                resultSet.getLong("analysis_request_id"),
                resultSet.getString("root_cause"),
                resultSet.getString("attempted_steps"),
                resultSet.getString("final_solution"),
                resultSet.getString("test_result"),
                ResolutionStatus.valueOf(resultSet.getString("resolution_status")),
                resultSet.getObject("created_at", OffsetDateTime.class),
                resultSet.getObject("updated_at", OffsetDateTime.class)), id, userId).stream().findFirst();
    }

    public boolean saveResolutionNote(long id, long userId, AnalysisResolutionNoteRequest request) {
        return jdbcTemplate.update("""
                INSERT INTO analysis_resolution_notes (
                    analysis_request_id, root_cause, attempted_steps, final_solution, test_result, resolution_status)
                SELECT a.id, ?, ?, ?, ?, ?
                FROM analysis_requests a
                WHERE a.id = ? AND a.user_id = ?
                ON CONFLICT (analysis_request_id) DO UPDATE
                SET root_cause = EXCLUDED.root_cause,
                    attempted_steps = EXCLUDED.attempted_steps,
                    final_solution = EXCLUDED.final_solution,
                    test_result = EXCLUDED.test_result,
                    resolution_status = EXCLUDED.resolution_status,
                    updated_at = CURRENT_TIMESTAMP
                """, request.rootCause() == null ? "" : request.rootCause(),
                request.attemptedSteps() == null ? "" : request.attemptedSteps(),
                request.finalSolution() == null ? "" : request.finalSolution(),
                request.testResult() == null ? "" : request.testResult(),
                request.resolutionStatus().name(), id, userId) == 1;
    }

    
/** 소유자 조건을 적용해 상세 정보와 저장된 AI 결과를 조회한다. */
    public Optional<AnalysisDetailResponse> findDetailByIdAndUserId(long id, long userId) {
        return jdbcTemplate.query("""
                SELECT a.id, a.language, a.error_message, a.analysis_status, a.created_at, r.completed_at,
                       r.result_json::text AS ai_analysis_result
                FROM analysis_requests a
                LEFT JOIN analysis_results r ON r.analysis_request_id = a.id
                WHERE a.id = ? AND a.user_id = ?
                """, (resultSet, rowNumber) -> {
                    String resultJson = resultSet.getString("ai_analysis_result");
                    AiAnalysisResponse result = null;
                    if (resultJson != null) {
                        try {
                            result = objectMapper.readValue(resultJson, AiAnalysisResponse.class);
                        } catch (JsonProcessingException invalidStoredResult) {
                            throw new DataRetrievalFailureException("Stored AI result is invalid");
                        }
                    }
                    return new AnalysisDetailResponse(
                            resultSet.getLong("id"),
                            resultSet.getString("language"),
                            resultSet.getString("error_message"),
                            resultSet.getString("analysis_status"),
                            resultSet.getObject("created_at", OffsetDateTime.class),
                            resultSet.getObject("completed_at", OffsetDateTime.class),
                            result);
                }, id, userId).stream().findFirst();
    }

    @Transactional
    
/** 소유자 조건을 확인하며 결과 저장과 완료 상태 변경을 트랜잭션으로 처리한다. */
    public boolean saveAiResultAndComplete(long id, long userId, String resultJson) {
        int updated = jdbcTemplate.update("""
                UPDATE analysis_requests SET analysis_status = 'COMPLETED'
                WHERE id = ? AND user_id = ?
                """, id, userId);
        if (updated == 0) return false;
        jdbcTemplate.update("""
                INSERT INTO analysis_results (analysis_request_id, result_json, completed_at)
                VALUES (?, CAST(? AS jsonb), CURRENT_TIMESTAMP)
                ON CONFLICT (analysis_request_id) DO UPDATE
                SET result_json = EXCLUDED.result_json, completed_at = EXCLUDED.completed_at
                """, id, resultJson);
        return true;
    }

    
/** 소유자 조건에 해당하는 분석을 실패 상태로 기록한다. */
    public void markAiAnalysisFailed(long id, long userId) {
        jdbcTemplate.update("""
                UPDATE analysis_requests SET analysis_status = 'FAILED'
                WHERE id = ? AND user_id = ?
                """, id, userId);
    }

    
/** 분석 ID와 소유자 ID가 일치하는 행만 삭제한다. */
    public boolean deleteByIdAndUserId(long id, long userId) {
        return jdbcTemplate.update("""
                DELETE FROM analysis_requests
                WHERE id = ? AND user_id = ?
                """, id, userId) == 1;
    }
}
