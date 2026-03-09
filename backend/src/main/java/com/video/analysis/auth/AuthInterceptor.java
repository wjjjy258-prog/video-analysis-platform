package com.video.analysis.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    private final AuthService authService;
    private final ObjectMapper objectMapper;

    public AuthInterceptor(AuthService authService, ObjectMapper objectMapper) {
        this.authService = authService;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }

        String token = resolveToken(request);
        AuthPrincipal principal = authService.resolveToken(token);
        if (principal == null) {
            writeUnauthorized(response, "请先登录。");
            return false;
        }

        AuthContext.set(principal);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        AuthContext.clear();
    }

    private String resolveToken(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization != null) {
            String value = authorization.trim();
            if (value.regionMatches(true, 0, "Bearer ", 0, 7)) {
                return value.substring(7).trim();
            }
            if (!value.isEmpty()) {
                return value;
            }
        }
        String fallback = request.getHeader("X-Auth-Token");
        return fallback == null ? null : fallback.trim();
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws Exception {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(Map.of(
                "success", false,
                "message", message
        )));
    }
}
