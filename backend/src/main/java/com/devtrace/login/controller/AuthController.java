package com.devtrace.login.controller;

import com.devtrace.global.exception.ApiErrorResponse;
import com.devtrace.login.domain.UserPrincipal;
import com.devtrace.login.dto.*;
import com.devtrace.login.exception.EmailAlreadyRegisteredException;
import com.devtrace.login.exception.InvalidSignupException;
import com.devtrace.login.service.AuthService;

import java.time.Duration;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")

/** 회원 인증 API와 CSRF 토큰 조회를 제공하고 로그인 세션을 관리한다. */
public class AuthController {
    private final AuthService authService;
    private final SecurityContextRepository securityContextRepository;
    private final SessionAuthenticationStrategy sessionAuthenticationStrategy;
    private final String sessionCookieName;
    private final boolean secureCookies;
    private final String sessionCookieSameSite;

    public AuthController(
            AuthService authService,
            SecurityContextRepository securityContextRepository,
            SessionAuthenticationStrategy sessionAuthenticationStrategy,
            @Value("${server.servlet.session.cookie.name:JSESSIONID}") String sessionCookieName,
            @Value("${server.servlet.session.cookie.secure:false}") boolean secureCookies,
            @Value("${server.servlet.session.cookie.same-site:lax}") String sessionCookieSameSite) {
        this.authService = authService;
        this.securityContextRepository = securityContextRepository;
        this.sessionAuthenticationStrategy = sessionAuthenticationStrategy;
        this.sessionCookieName = sessionCookieName;
        this.secureCookies = secureCookies;
        this.sessionCookieSameSite = sessionCookieSameSite;
    }

    @PostMapping("/signup")
    
/** 회원가입 요청을 검증하고 생성된 사용자의 공개 정보를 반환한다. */
    public ResponseEntity<UserResponse> signup(@Valid @RequestBody SignupRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.signup(request));
    }

    @PostMapping("/login")
    
/** 자격 증명을 인증하고 세션 고정 공격 방지를 적용한 뒤 사용자 정보를 반환한다. */
    public UserResponse login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse) {
        Authentication authentication = authService.authenticate(request);
        sessionAuthenticationStrategy.onAuthentication(authentication, servletRequest, servletResponse);

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, servletRequest, servletResponse);
        return UserResponse.from((UserPrincipal) authentication.getPrincipal());
    }

    @PostMapping("/logout")
    
/** 현재 인증 세션을 무효화하고 세션 쿠키 만료 응답을 반환한다. */
    public ResponseEntity<Void> logout(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication) {
        new SecurityContextLogoutHandler().logout(request, response, authentication);
        String path = request.getContextPath().isBlank() ? "/" : request.getContextPath();
        ResponseCookie expiredCookie = ResponseCookie.from(sessionCookieName, "")
                .httpOnly(true)
                .secure(secureCookies || request.isSecure())
                .sameSite(sessionCookieSameSite)
                .path(path)
                .maxAge(Duration.ZERO)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, expiredCookie.toString());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    
/** 현재 세션의 인증 사용자 정보를 반환한다. */
    public UserResponse me(
            @org.springframework.security.core.annotation.AuthenticationPrincipal UserPrincipal principal) {
        return UserResponse.from(principal);
    }

    @GetMapping("/csrf")
    
/** 상태 변경 요청에 사용할 CSRF 헤더 이름과 토큰을 반환한다. */
    public CsrfResponse csrf(@RequestAttribute("_csrf") CsrfToken csrfToken) {
        return new CsrfResponse(csrfToken.getHeaderName(), csrfToken.getToken());
    }

    @ExceptionHandler(EmailAlreadyRegisteredException.class)
    
/** 중복 이메일 가입 오류를 클라이언트용 응답으로 변환한다. */
    public ResponseEntity<ApiErrorResponse> duplicateEmail() {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiErrorResponse(false, "해당 이메일로 가입할 수 없습니다.", java.util.List.of()));
    }

    @ExceptionHandler(InvalidSignupException.class)
    
/** 회원가입 검증 오류를 클라이언트용 응답으로 변환한다. */
    public ResponseEntity<ApiErrorResponse> invalidSignup() {
        return ResponseEntity.badRequest()
                .body(new ApiErrorResponse(false, "입력값을 확인해 주세요.", java.util.List.of()));
    }

    @ExceptionHandler(AuthenticationException.class)
    
/** 로그인 실패를 계정 존재 여부와 무관한 응답으로 변환한다. */
    public ResponseEntity<ApiErrorResponse> invalidCredentials() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiErrorResponse(false, "이메일 또는 비밀번호를 확인해 주세요.", java.util.List.of()));
    }
}
