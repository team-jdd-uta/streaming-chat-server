package com.example.demo.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                // 모든 출처(도메인/포트)를 허용한다.
                .allowedOriginPatterns("*")
                // 모든 HTTP 메서드를 허용한다.
                .allowedMethods("*")
                // 모든 요청 헤더를 허용한다.
                .allowedHeaders("*")
                // 프리플라이트 캐시 시간(초)
                .maxAge(3600);
    }
}
