package db.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.MessageDigest;
import java.util.HexFormat;

import org.junit.jupiter.api.Test;


/** 마이그레이션 데이터 digest가 다섯 분석 컬럼의 변경을 감지하는지 검증한다. */
class V2MigrationDigestTest {
    @Test
    void digestIncludesAllFiveAnalysisColumns() throws Exception {
        String baseline = digest("42", "Java", "original error", "PENDING", "2026-01-01 00:00:00+00");

        assertThat(digest("43", "Java", "original error", "PENDING", "2026-01-01 00:00:00+00"))
                .isNotEqualTo(baseline);
        assertThat(digest("42", "Kotlin", "original error", "PENDING", "2026-01-01 00:00:00+00"))
                .isNotEqualTo(baseline);
        assertThat(digest("42", "Java", "changed error", "PENDING", "2026-01-01 00:00:00+00"))
                .isNotEqualTo(baseline);
        assertThat(digest("42", "Java", "original error", "FAILED", "2026-01-01 00:00:00+00"))
                .isNotEqualTo(baseline);
        assertThat(digest("42", "Java", "original error", "PENDING", "2026-01-02 00:00:00+00"))
                .isNotEqualTo(baseline);
    }

    private String digest(String id, String language, String errorMessage, String analysisStatus, String createdAt)
            throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        V2__create_users_and_assign_analyses.updateRowDigest(
                digest, id, language, errorMessage, analysisStatus, createdAt);
        return HexFormat.of().formatHex(digest.digest());
    }
}
