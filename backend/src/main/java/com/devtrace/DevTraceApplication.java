package com.devtrace;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication

/** Spring Boot 애플리케이션의 시작점이며 com.devtrace 하위 컴포넌트를 검색해 서버를 실행한다. */
public class DevTraceApplication {
    public static void main(String[] args) {
        SpringApplication.run(DevTraceApplication.class, args);
    }
}