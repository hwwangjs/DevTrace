package com.devtrace.login.service;

import com.devtrace.login.domain.UserAccount;
import com.devtrace.login.dto.SignupRequest;
import com.devtrace.login.dto.UserResponse;
import com.devtrace.login.exception.EmailAlreadyRegisteredException;
import com.devtrace.login.repository.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@ExtendWith(MockitoExtension.class)

/** 회원가입 처리, 이메일 중복 및 비밀번호 해시 동작을 검증한다. */
class AuthServiceTest {
    private static final String PASSWORD = "unit-test-password-91";

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuthenticationManager authenticationManager;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private AuthService authService;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, passwordEncoder, authenticationManager);
    }

    @Test
    void signupNormalizesEmailAndPersistsOnlyBcryptPasswordHash() {
        when(userRepository.createUser(eq("person@example.com"), anyString(), eq("Dev")))
                .thenAnswer(invocation -> {
                    String hash = invocation.getArgument(1);
                    assertThat(hash).startsWith("$2a$").isNotEqualTo(PASSWORD);
                    assertThat(passwordEncoder.matches(PASSWORD, hash)).isTrue();
                    return new UserAccount(9L, "person@example.com", hash, "Dev", "USER");
                });

        UserResponse response = authService.signup(new SignupRequest(" Person@Example.COM ", PASSWORD, " Dev "));

        assertThat(response).isEqualTo(new UserResponse(9L, "person@example.com", "Dev", "USER"));
        verify(userRepository).createUser(eq("person@example.com"), anyString(), eq("Dev"));
    }

    @Test
    void signupConvertsUniqueEmailViolationToConflictException() {
        doThrow(new DuplicateKeyException("unique constraint"))
                .when(userRepository).createUser(eq("person@example.com"), anyString(), eq("Dev"));

        assertThatThrownBy(() -> authService.signup(new SignupRequest("person@example.com", PASSWORD, "Dev")))
                .isInstanceOf(EmailAlreadyRegisteredException.class);
    }
}
