package com.devtrace.login.dto;

import com.devtrace.login.domain.UserAccount;
import com.devtrace.login.domain.UserPrincipal;


/** 인증 사용자에게 공개 가능한 ID, 이메일, 닉네임 및 역할을 표현한다. */
public record UserResponse(long id, String email, String nickname, String role) {
    public static UserResponse from(UserPrincipal principal) {
        return new UserResponse(principal.id(), principal.email(), principal.nickname(), principal.role());
    }

    public static UserResponse from(UserAccount account) {
        return new UserResponse(account.id(), account.email(), account.nickname(), account.role());
    }
}
