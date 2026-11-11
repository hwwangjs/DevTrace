package com.devtrace.global.web;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Forwards browser routes to the React entry point while leaving APIs and assets untouched. */
@Component
public class SpaForwardingFilter extends OncePerRequestFilter {
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!"GET".equals(request.getMethod())) {
            return true;
        }

        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (path.equals("/") || path.equals("/api") || path.startsWith("/api/")) {
            return true;
        }

        int lastSlash = path.lastIndexOf('/');
        return path.substring(lastSlash + 1).contains(".");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        request.getRequestDispatcher("/index.html").forward(request, response);
    }
}
