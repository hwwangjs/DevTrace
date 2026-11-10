package com.devtrace.login.service;

import com.devtrace.login.domain.UserAccount;
import com.devtrace.login.dto.*;
import com.devtrace.login.exception.EmailAlreadyRegisteredException;
import com.devtrace.login.exception.InvalidSignupException;
import com.devtrace.login.repository.UserRepository;

import java.nio.charset.StandardCharsets;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service

/** 회원가입 입력을 처리하고 비밀번호 해시 저장 및 자격 증명 인증을 위임한다. */
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
    }

    
/** 비밀번호 길이를 검사하고 BCrypt 해시를 저장한 뒤 공개 사용자 정보를 반환한다. */
    public UserResponse signup(SignupRequest request) {
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new InvalidSignupException();
        }
        String passwordHash = passwordEncoder.encode(request.password());
        try {
            UserAccount account = userRepository.createUser(request.email(), passwordHash, request.nickname());
            return UserResponse.from(account);
        } catch (DuplicateKeyException duplicateEmail) {
            throw new EmailAlreadyRegisteredException();
        }
    }

    
/** Spring Security 인증 관리자에 이메일과 비밀번호 검증을 위임한다. */
    public Authentication authenticate(LoginRequest request) {
        return authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(request.email(), request.password()));
    }
}
