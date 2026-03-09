package com.video.analysis.controller;

import com.video.analysis.auth.AuthService;
import com.video.analysis.dto.auth.AuthResponse;
import com.video.analysis.dto.auth.LoginRequest;
import com.video.analysis.dto.auth.RegisterRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private static final int MAX_TOKEN_LENGTH = 200;

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest) {
        return authService.login(request, resolveClientIp(servletRequest));
    }

    @PostMapping("/logout")
    public AuthResponse logout(@RequestHeader(value = "Authorization", required = false) String authorization) {
        String token = extractToken(authorization);
        authService.logout(token);
        AuthResponse response = new AuthResponse();
        response.setSuccess(true);
        response.setMessage("已退出登录。");
        return response;
    }

    @GetMapping("/me")
    public AuthResponse me(@RequestHeader(value = "Authorization", required = false) String authorization) {
        return authService.current(extractToken(authorization));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<AuthResponse> handleValidationException(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getDefaultMessage())
                .orElse("请求参数校验失败。");
        return ResponseEntity.badRequest().body(AuthResponse.fail(message));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<AuthResponse> handleResponseStatusException(ResponseStatusException ex) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        HttpStatus safeStatus = status == null ? HttpStatus.BAD_REQUEST : status;
        return ResponseEntity.status(safeStatus).body(AuthResponse.fail(ex.getReason() == null ? "请求失败。" : ex.getReason()));
    }

    private String extractToken(String authorization) {
        if (authorization == null) {
            return null;
        }
        String value = authorization.trim();
        if (value.regionMatches(true, 0, "Bearer ", 0, 7)) {
            String token = value.substring(7).trim();
            return token.length() > MAX_TOKEN_LENGTH ? null : token;
        }
        return value.length() > MAX_TOKEN_LENGTH ? null : value;
    }

    private String resolveClientIp(HttpServletRequest request) {
        String fromForward = request.getHeader("X-Forwarded-For");
        if (fromForward != null && !fromForward.isBlank()) {
            return fromForward.split(",")[0].trim();
        }
        String fromRealIp = request.getHeader("X-Real-IP");
        if (fromRealIp != null && !fromRealIp.isBlank()) {
            return fromRealIp.trim();
        }
        return request.getRemoteAddr();
    }
}
