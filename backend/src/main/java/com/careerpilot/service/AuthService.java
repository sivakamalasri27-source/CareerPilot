package com.careerpilot.service;

import com.careerpilot.dto.auth.AuthResponse;
import com.careerpilot.dto.auth.LoginRequest;
import com.careerpilot.dto.auth.RefreshTokenRequest;
import com.careerpilot.dto.auth.RegisterRequest;
import com.careerpilot.dto.auth.UserResponse;

public interface AuthService {

    AuthResponse register(RegisterRequest request);

    AuthResponse login(LoginRequest request);

    AuthResponse refreshToken(RefreshTokenRequest request);

    void logout(RefreshTokenRequest request);

    UserResponse getCurrentUser(String email);
}
