package com.devtrace.login.repository;

import com.devtrace.login.domain.UserAccount;

import java.util.Optional;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository

/** JdbcTemplate으로 이메일 기준 사용자 조회와 USER 권한 계정 생성을 수행한다. */
public class UserRepository {
    private final JdbcTemplate jdbcTemplate;

    public UserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    
/** 이메일로 계정을 조회하며 존재하지 않으면 빈 Optional을 반환한다. */
    public Optional<UserAccount> findByEmail(String normalizedEmail) {
        return jdbcTemplate.query("""
                SELECT id, email, password_hash, nickname, role
                FROM users
                WHERE email = ?
                """, (resultSet, rowNumber) -> new UserAccount(
                resultSet.getLong("id"),
                resultSet.getString("email"),
                resultSet.getString("password_hash"),
                resultSet.getString("nickname"),
                resultSet.getString("role")), normalizedEmail).stream().findFirst();
    }

    
/** 사용자 입력과 비밀번호 해시를 USER 권한으로 저장하고 생성된 계정을 반환한다. */
    public UserAccount createUser(String normalizedEmail, String passwordHash, String nickname) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO users (email, password_hash, nickname, role)
                VALUES (?, ?, ?, 'USER')
                RETURNING id, email, password_hash, nickname, role
                """, (resultSet, rowNumber) -> new UserAccount(
                resultSet.getLong("id"),
                resultSet.getString("email"),
                resultSet.getString("password_hash"),
                resultSet.getString("nickname"),
                resultSet.getString("role")), normalizedEmail, passwordHash, nickname);
    }
}
