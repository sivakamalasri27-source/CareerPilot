package com.careerpilot.service.impl;

import com.careerpilot.dto.auth.AuthResponse;
import com.careerpilot.dto.auth.LoginRequest;
import com.careerpilot.dto.auth.RefreshTokenRequest;
import com.careerpilot.dto.auth.RegisterRequest;
import com.careerpilot.dto.auth.UserResponse;
import com.careerpilot.entity.Profile;
import com.careerpilot.entity.RefreshToken;
import com.careerpilot.entity.Role;
import com.careerpilot.entity.User;
import com.careerpilot.exception.DuplicateResourceException;
import com.careerpilot.exception.InvalidTokenException;
import com.careerpilot.exception.ResourceNotFoundException;
import com.careerpilot.repository.ProfileRepository;
import com.careerpilot.repository.RefreshTokenRepository;
import com.careerpilot.repository.UserRepository;
import com.careerpilot.security.CustomUserDetails;
import com.careerpilot.security.JwtService;
import com.careerpilot.security.TokenHashUtil;
import com.careerpilot.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final ProfileRepository profileRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    @Value("${security.jwt.refresh-expiration-ms:604800000}")
    private long refreshExpirationMs;

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase();

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new DuplicateResourceException("Email is already registered: " + normalizedEmail);
        }

        User user = User.builder()
                .email(normalizedEmail)
                .fullName(request.getFullName().trim())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role(Role.USER)
                .isActive(true)
                .build();

        User savedUser = userRepository.save(user);

        // Auto-provision initial profile for the fresher
        Profile profile = Profile.builder()
                .user(savedUser)
                .build();
        profileRepository.save(profile);

        CustomUserDetails userDetails = new CustomUserDetails(savedUser);
        String accessToken = jwtService.generateToken(userDetails, savedUser.getId());
        String rawRefreshToken = generateAndSaveRefreshToken(savedUser);

        log.info("Registered new user with ID: {}", savedUser.getId());

        return buildAuthResponse(accessToken, rawRefreshToken, savedUser);
    }

    @Override
    @Transactional
    public AuthResponse login(LoginRequest request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase();

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(normalizedEmail, request.getPassword())
        );

        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        User user = userRepository.findById(userDetails.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + normalizedEmail));

        String accessToken = jwtService.generateToken(userDetails, user.getId());
        String rawRefreshToken = generateAndSaveRefreshToken(user);

        log.info("User logged in successfully: {}", normalizedEmail);

        return buildAuthResponse(accessToken, rawRefreshToken, user);
    }

    @Override
    @Transactional(noRollbackFor = InvalidTokenException.class)
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        String rawToken = request.getRefreshToken();
        String tokenHash = TokenHashUtil.hash(rawToken);

        RefreshToken token = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new InvalidTokenException("Invalid refresh token"));

        User user = token.getUser();

        // Reuse detection: If token was already revoked or expired, revoke ALL active tokens for this user
        if (Boolean.TRUE.equals(token.getRevoked()) || token.getExpiresAt().isBefore(Instant.now())) {
            log.warn("Security Alert: Refresh token reuse or expired token detected for user ID {}. Revoking all active tokens.", user.getId());
            refreshTokenRepository.revokeAllActiveTokensByUserId(user.getId());
            throw new InvalidTokenException("Refresh token reuse detected. All sessions have been revoked. Please log in again.");
        }

        // Single-use token rotation: Revoke the presented token
        token.setRevoked(true);
        refreshTokenRepository.save(token);

        // Issue new refresh token
        String newRawRefreshToken = generateAndSaveRefreshToken(user);

        // Issue new access token
        CustomUserDetails userDetails = new CustomUserDetails(user);
        String newAccessToken = jwtService.generateToken(userDetails, user.getId());

        log.info("Successfully rotated refresh token for user ID: {}", user.getId());

        return buildAuthResponse(newAccessToken, newRawRefreshToken, user);
    }

    @Override
    @Transactional
    public void logout(RefreshTokenRequest request) {
        if (request != null && request.getRefreshToken() != null && !request.getRefreshToken().isBlank()) {
            String tokenHash = TokenHashUtil.hash(request.getRefreshToken());
            refreshTokenRepository.findByTokenHash(tokenHash).ifPresent(token -> {
                token.setRevoked(true);
                refreshTokenRepository.save(token);
                log.info("Revoked refresh token on logout for user ID: {}", token.getUser().getId());
            });
        }
        SecurityContextHolder.clearContext();
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));
        return toUserResponse(user);
    }

    private String generateAndSaveRefreshToken(User user) {
        String rawToken = UUID.randomUUID().toString() + "-" + UUID.randomUUID().toString();
        String tokenHash = TokenHashUtil.hash(rawToken);

        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .tokenHash(tokenHash)
                .expiresAt(Instant.now().plusMillis(refreshExpirationMs))
                .revoked(false)
                .build();

        refreshTokenRepository.save(refreshToken);
        return rawToken;
    }

    private AuthResponse buildAuthResponse(String accessToken, String refreshToken, User user) {
        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresInMs(jwtService.getExpirationMs())
                .user(toUserResponse(user))
                .build();
    }

    private UserResponse toUserResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .role(user.getRole())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
