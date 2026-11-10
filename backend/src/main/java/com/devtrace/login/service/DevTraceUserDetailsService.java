package com.devtrace.login.service;

import com.devtrace.login.domain.UserAccount;
import com.devtrace.login.domain.UserPrincipal;
import com.devtrace.login.repository.UserRepository;

import java.util.Locale;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service

/** 이메일을 정규화해 사용자를 조회하고 Spring Security 사용자 정보를 반환한다. */
public class DevTraceUserDetailsService implements UserDetailsService {
    private final UserRepository userRepository;

    public DevTraceUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    
/** 정규화한 이메일로 계정을 찾아 인증에 사용할 principal을 반환한다. */
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        String normalizedEmail = email.strip().toLowerCase(Locale.ROOT);
        return userRepository.findByEmail(normalizedEmail)
                .map(UserPrincipal::new)
                .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
    }
}
