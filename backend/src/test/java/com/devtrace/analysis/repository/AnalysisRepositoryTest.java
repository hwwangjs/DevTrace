package com.devtrace.analysis.repository;

import com.devtrace.analysis.dto.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

@ExtendWith(MockitoExtension.class)

/** 분석 SQL의 사용자 조건과 JdbcTemplate 파라미터 전달을 검증한다. */
class AnalysisRepositoryTest {
    @Mock
    private JdbcTemplate jdbcTemplate;

    @Test
    void savePassesServerUserIdAsInsertParameter() {
        AnalysisRepository repository = new AnalysisRepository(jdbcTemplate, new ObjectMapper());

        repository.save(new AnalysisRequest("Java", "NullPointerException"), 41L);

        verify(jdbcTemplate).update(
                contains("user_id"), eq("Java"), eq("NullPointerException"), eq("PENDING"), eq(41L));
    }

    @Test
    void findAllFiltersByUserIdInSqlAndQueryParameter() {
        AnalysisRepository repository = new AnalysisRepository(jdbcTemplate, new ObjectMapper());
        var expected = List.of(new AnalysisListItem(
                7L, "Java", "A error", "PENDING", OffsetDateTime.parse("2026-10-30T12:00:00Z")));
        when(jdbcTemplate.query(any(String.class), any(RowMapper.class), eq(41L))).thenReturn(expected);

        List<AnalysisListItem> actual = repository.findAll(41L);

        assertThat(actual).containsExactlyElementsOf(expected);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(sql.capture(), any(RowMapper.class), eq(41L));
        assertThat(sql.getValue()).contains("WHERE user_id = ?");
    }

    @Test
    void findByIdAndUserIdRestrictsDetailQueryToBothIdentifiers() {
        AnalysisRepository repository = new AnalysisRepository(jdbcTemplate, new ObjectMapper());
        when(jdbcTemplate.query(any(String.class), any(RowMapper.class), eq(7L), eq(41L)))
                .thenReturn(List.of(new AnalysisListItem(
                        7L, "Java", "A error", "PENDING", OffsetDateTime.parse("2026-10-30T12:00:00Z"))));

        Optional<AnalysisListItem> actual = repository.findByIdAndUserId(7L, 41L);

        assertThat(actual).isPresent();
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(sql.capture(), any(RowMapper.class), eq(7L), eq(41L));
        assertThat(sql.getValue()).contains("WHERE id = ? AND user_id = ?");
    }

    @Test
    void deleteByIdAndUserIdRestrictsDeleteToBothIdentifiers() {
        AnalysisRepository repository = new AnalysisRepository(jdbcTemplate, new ObjectMapper());
        when(jdbcTemplate.update(any(String.class), eq(7L), eq(41L))).thenReturn(1);

        assertThat(repository.deleteByIdAndUserId(7L, 41L)).isTrue();

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).update(sql.capture(), eq(7L), eq(41L));
        assertThat(sql.getValue()).contains("WHERE id = ? AND user_id = ?");
    }

    @Test
    void persistsResultAndCompletesOnlyTheOwnedAnalysisInOneTransaction() {
        AnalysisRepository repository = new AnalysisRepository(jdbcTemplate, new ObjectMapper());
        when(jdbcTemplate.update(contains("UPDATE analysis_requests"), eq(7L), eq(41L))).thenReturn(1);

        assertThat(repository.saveAiResultAndComplete(7L, 41L, "{\"summary\":\"safe\"}")).isTrue();

        verify(jdbcTemplate).update(contains("WHERE id = ? AND user_id = ?"), eq(7L), eq(41L));
        verify(jdbcTemplate).update(contains("INSERT INTO analysis_results"), eq(7L), eq("{\"summary\":\"safe\"}"));
    }

    @Test
    void detailQueryIsOwnerScopedAndKeepsRowsWithoutAResult() {
        AnalysisRepository repository = new AnalysisRepository(jdbcTemplate, new ObjectMapper());
        when(jdbcTemplate.query(contains("LEFT JOIN analysis_results"), any(RowMapper.class), eq(7L), eq(41L)))
                .thenReturn(List.of());

        assertThat(repository.findDetailByIdAndUserId(7L, 41L)).isEmpty();

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(sql.capture(), any(RowMapper.class), eq(7L), eq(41L));
        assertThat(sql.getValue()).contains("LEFT JOIN analysis_results")
                .contains("WHERE a.id = ? AND a.user_id = ?");
    }
}
