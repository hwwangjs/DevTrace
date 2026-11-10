package com.devtrace.login.domain;



/** DB에서 조회한 사용자 식별 정보와 비밀번호 해시를 나타내며 문자열 출력에서 해시를 감춘다. */
public record UserAccount(long id, String email, String passwordHash, String nickname, String role) {
    @Override
    public String toString() {
        return "UserAccount[id=" + id + ", email=" + email + ", passwordHash=<omitted>, nickname=" + nickname
                + ", role=" + role + "]";
    }
}
