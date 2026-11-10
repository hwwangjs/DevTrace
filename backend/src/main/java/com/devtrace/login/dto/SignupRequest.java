package com.devtrace.login.dto;

import java.util.Locale;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;


/** 회원가입 이메일·비밀번호·닉네임을 전달하고 형식 검증과 정규화를 수행한다. */
public record SignupRequest(
        @NotBlank @Email @Size(max = 320) String email,
        @NotBlank @Size(min = 12, max = 72) String password,
        @NotBlank @Size(max = 80) String nickname) {

    public SignupRequest {
        if (email != null) email = email.strip().toLowerCase(Locale.ROOT);
        if (nickname != null) nickname = nickname.strip();
    }

    @Override
    public String toString() {
        return "SignupRequest[email=" + email + ", password=<omitted>, nickname=" + nickname + "]";
    }
}
