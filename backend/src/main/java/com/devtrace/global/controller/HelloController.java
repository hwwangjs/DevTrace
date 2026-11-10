package com.devtrace.global.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/api")

/** 프런트엔드와 백엔드 연결 상태를 확인하는 인사 API를 제공한다. */
public class HelloController {
    @GetMapping("/hello")
    public Map<String, String> hello() {
        return Map.of("message", "DevTrace 백엔드 연결 성공!");
    }
}