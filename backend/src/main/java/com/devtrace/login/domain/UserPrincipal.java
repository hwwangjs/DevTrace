package com.devtrace.login.domain;

import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;



/** 인증된 사용자 정보를 세션에 제공하고 Spring Security 권한 및 사용자 ID를 반환한다. */
public final class UserPrincipal implements UserDetails {
    private final long id;
    private final String email;
    private final String passwordHash;
    private final String nickname;
    private final String role;

    public UserPrincipal(UserAccount account) {
        this.id = account.id();
        this.email = account.email();
        this.passwordHash = account.passwordHash();
        this.nickname = account.nickname();
        this.role = account.role();
    }

    public long id() {
        return id;
    }

    public String email() {
        return email;
    }

    public String nickname() {
        return nickname;
    }

    public String role() {
        return role;
    }

/** 사용자 역할을 Spring Security 권한 형식으로 제공한다. */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public String toString() {
        return "UserPrincipal[id=" + id + ", email=" + email + ", nickname=" + nickname + ", role=" + role + "]";
    }
}
