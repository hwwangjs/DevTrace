package com.devtrace.login.dto;

import java.util.Locale;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;


/** 이메일과 비밀번호를 전달하고 이메일을 정규화하며 비밀번호 출력을 감춘다. */
public record LoginRequest(
        @NotBlank @Email @Size(max = 320) String email,
        @NotBlank @Size(max = 72) String password) {

    public LoginRequest {
        if (email != null) email = email.strip().toLowerCase(Locale.ROOT);
    }

    @Override
    public String toString() {
        return "LoginRequest[email=" + email + ", password=<omitted>]";
    }
}
