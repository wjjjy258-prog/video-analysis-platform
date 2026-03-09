package com.video.analysis.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;

@Configuration
public class CorsConfig implements WebMvcConfigurer {

    private static final String[] DEFAULT_ALLOWED_ORIGINS = {
            "http://localhost:5173",
            "http://127.0.0.1:5173"
    };

    private final String[] allowedOrigins;

    public CorsConfig(@Value("${app.security.allowed-origins:http://localhost:5173,http://127.0.0.1:5173}") String origins) {
        this.allowedOrigins = Arrays.stream(origins.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toArray(String[]::new);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        String[] origins = allowedOrigins.length == 0 ? DEFAULT_ALLOWED_ORIGINS : allowedOrigins;
        registry.addMapping("/**")
                .allowedOriginPatterns(origins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("Authorization", "Content-Type", "X-Auth-Token")
                .maxAge(3600);
    }
}
