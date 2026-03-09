package com.video.analysis.auth;

public record AuthPrincipal(Long userId, String username, String token) {
}
