package db.migration;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HexFormat;
import java.util.Locale;

import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;



/** 사용자 테이블과 초기 관리자 계정을 만들고 기존 분석 행을 관리자에게 연결하는 Flyway 마이그레이션이다. */
public class V2__create_users_and_assign_analyses extends BaseJavaMigration {
    private static final String ADMIN_EMAIL_ENV = "DEVTRACE_INITIAL_ADMIN_EMAIL";
    private static final String ADMIN_NICKNAME_ENV = "DEVTRACE_INITIAL_ADMIN_NICKNAME";
    private static final String ADMIN_PASSWORD_ENV = "DEVTRACE_INITIAL_ADMIN_PASSWORD";

    @Override
    public boolean canExecuteInTransaction() {
        return true;
    }

    @Override
    
/** 사용자 테이블을 준비하고 관리자 연결 및 기존 분석 데이터 보존을 검증한다. */
    public void migrate(Context context) throws Exception {
        String email = requiredEnvironmentValue(ADMIN_EMAIL_ENV).strip().toLowerCase(Locale.ROOT);
        String nickname = requiredEnvironmentValue(ADMIN_NICKNAME_ENV).strip();
        String password = requiredEnvironmentValue(ADMIN_PASSWORD_ENV);
        validateConfiguration(email, nickname, password);

        Connection connection = context.getConnection();
        DataSnapshot before = snapshot(connection);
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS users (
                        id BIGSERIAL PRIMARY KEY,
                        email VARCHAR(320) NOT NULL UNIQUE,
                        password_hash VARCHAR(100) NOT NULL,
                        nickname VARCHAR(80) NOT NULL,
                        role VARCHAR(16) NOT NULL CHECK (role IN ('ADMIN', 'USER')),
                        created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        CONSTRAINT users_email_normalized CHECK (email = lower(btrim(email)))
                    )
                    """);
            statement.execute("ALTER TABLE analysis_requests ADD COLUMN IF NOT EXISTS user_id BIGINT");
        }

        long administratorId = ensureAdministrator(connection, email, nickname, password);
        try (PreparedStatement update = connection.prepareStatement(
                "UPDATE analysis_requests SET user_id = ? WHERE user_id IS NULL")) {
            update.setLong(1, administratorId);
            update.executeUpdate();
        }

        DataSnapshot after = snapshot(connection);
        if (before.rowCount() != after.rowCount() || !before.digest().equals(after.digest())) {
            throw migrationFailed();
        }
        try (PreparedStatement unowned = connection.prepareStatement(
                "SELECT count(*) FROM analysis_requests WHERE user_id IS NULL OR user_id <> ?")) {
            unowned.setLong(1, administratorId);
            try (ResultSet result = unowned.executeQuery()) {
                if (!result.next() || result.getLong(1) != 0) throw migrationFailed();
            }
        }

        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE analysis_requests ALTER COLUMN user_id SET NOT NULL");
            statement.execute("""
                    DO $$
                    BEGIN
                        IF NOT EXISTS (
                            SELECT 1 FROM pg_constraint
                            WHERE conrelid = 'analysis_requests'::regclass
                              AND conname = 'analysis_requests_user_id_fkey'
                        ) THEN
                            ALTER TABLE analysis_requests
                            ADD CONSTRAINT analysis_requests_user_id_fkey
                            FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT;
                        END IF;
                    END $$
                    """);
            statement.execute("""
                    CREATE INDEX IF NOT EXISTS analysis_requests_user_created_idx
                    ON analysis_requests (user_id, created_at DESC)
                    """);
        }
    }

    private long ensureAdministrator(Connection connection, String email, String nickname, String password)
            throws Exception {
        String passwordHash = hashPassword(password);
        try (PreparedStatement insert = connection.prepareStatement("""
                INSERT INTO users (email, password_hash, nickname, role)
                VALUES (?, ?, ?, 'ADMIN')
                ON CONFLICT (email) DO NOTHING
                """)) {
            insert.setString(1, email);
            insert.setString(2, passwordHash);
            insert.setString(3, nickname);
            insert.executeUpdate();
        }
        try (PreparedStatement select = connection.prepareStatement(
                "SELECT id, role FROM users WHERE email = ?")) {
            select.setString(1, email);
            try (ResultSet result = select.executeQuery()) {
                if (result.next() && "ADMIN".equals(result.getString("role"))) {
                    return result.getLong("id");
                }
            }
        }
        throw migrationFailed();
    }

/** 초기 관리자 비밀번호를 BCrypt 해시로 변환한다. */
    static String hashPassword(String password) {
        return new BCryptPasswordEncoder().encode(password);
    }

    private DataSnapshot snapshot(Connection connection) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        long count = 0;
        try (Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery(
                        "SELECT id, language, error_message, analysis_status, created_at "
                                + "FROM analysis_requests ORDER BY id")) {
            while (rows.next()) {
                updateRowDigest(digest,
                        Long.toString(rows.getLong("id")),
                        rows.getString("language"),
                        rows.getString("error_message"),
                        rows.getString("analysis_status"),
                        rows.getString("created_at"));
                count++;
            }
        }
        return new DataSnapshot(count, HexFormat.of().formatHex(digest.digest()));
    }

/** 분석 행의 주요 다섯 컬럼을 순서와 길이 정보를 포함해 digest에 반영한다. */
    static void updateRowDigest(MessageDigest digest, String id, String language, String errorMessage,
            String analysisStatus, String createdAt) {
        updateDigest(digest, id);
        updateDigest(digest, language);
        updateDigest(digest, errorMessage);
        updateDigest(digest, analysisStatus);
        updateDigest(digest, createdAt);
    }

    private static void updateDigest(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
    }

    private String requiredEnvironmentValue(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw migrationFailed();
        return value;
    }

    private void validateConfiguration(String email, String nickname, String password) {
        if (email.length() > 320 || !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw migrationFailed();
        }
        if (nickname.isBlank() || nickname.length() > 80) throw migrationFailed();
        if (password.length() < 12 || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw migrationFailed();
        }
    }

    private FlywayException migrationFailed() {
// 인증 정보, 개인정보, 저장된 로그가 마이그레이션 오류나 로그에 포함되지 않게 한다.
        return new FlywayException("Initial administrator/data migration failed; verify configuration and backup.");
    }

    
    /** 마이그레이션 전후 분석 행 수와 주요 컬럼 digest를 보관해 데이터 보존 여부를 비교한다. */
    private record DataSnapshot(long rowCount, String digest) {}
}
