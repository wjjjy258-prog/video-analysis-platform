package com.video.analysis.auth;

import com.video.analysis.dto.auth.AuthResponse;
import com.video.analysis.dto.auth.LoginRequest;
import com.video.analysis.dto.auth.RegisterRequest;

public interface AuthService {

    AuthResponse register(RegisterRequest request);

    AuthResponse login(LoginRequest request);

    void logout(String token);

    AuthResponse current(String token);

    AuthPrincipal resolveToken(String token);
}
