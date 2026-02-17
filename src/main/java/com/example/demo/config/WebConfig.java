package com.example.demo.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;


/*
*
* CORS 설정, Security 사용시 변경할것....
*
* */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // 로컬/도커 테스트 클라이언트 접근 허용
        // 운영 배포 시에는 허용 Origin을 환경별로 더 엄격히 제한해야 한다.
        registry.addMapping("/**") // 모든 API 허용
                .allowedOrigins(
                        "http://localhost:5173",
                        "http://localhost:5001",
                        "http://127.0.0.1:5173",
                        "http://127.0.0.1:5001"
                ) // 프론트 주소
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }
}
