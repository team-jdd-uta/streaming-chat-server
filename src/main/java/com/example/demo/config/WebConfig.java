package com.example.demo.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
            // 로컬 개발/테스트 + ngrok 도메인을 허용한다.
            .allowedOriginPatterns(
                "http://localhost:*",
                "http://127.0.0.1:*",
                "https://*.ngrok-free.app",
                "https://*.ngrok-free.dev"
            )
            // 모든 HTTP 메서드를 허용한다.
            .allowedMethods("*")
            // 모든 요청 헤더를 허용한다.
            .allowedHeaders("*")
            // 쿠키/인증 헤더가 필요한 요청도 허용한다.
            .allowCredentials(true)
            // 프리플라이트 캐시 시간(초)
            .maxAge(3600);
    }
}
