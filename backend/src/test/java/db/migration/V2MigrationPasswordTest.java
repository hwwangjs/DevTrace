package db.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;


/** 초기 관리자 비밀번호가 평문이 아닌 BCrypt 해시로 변환되는지 검증한다. */
class V2MigrationPasswordTest {
    @Test
    void initialAdministratorPasswordIsStoredAsBcryptHash() {
        String password = UUID.randomUUID() + UUID.randomUUID().toString();

        String hash = V2__create_users_and_assign_analyses.hashPassword(password);

        assertThat(hash).startsWith("$2a$").isNotEqualTo(password);
        assertThat(new BCryptPasswordEncoder().matches(password, hash)).isTrue();
    }
}
