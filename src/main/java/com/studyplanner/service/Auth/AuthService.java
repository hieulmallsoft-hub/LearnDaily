package com.studyplanner.service.Auth;

import java.time.LocalDateTime;
import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.studyplanner.dto.request.LoginRequest;
import com.studyplanner.dto.request.RefreshTokenRequest;
import com.studyplanner.dto.request.RegisterRequest;
import com.studyplanner.dto.response.AuthResponse;
import com.studyplanner.dto.response.UserInfo;
import com.studyplanner.exception.ApiException;
import com.studyplanner.model.refreshtoken.RefreshToken;
import com.studyplanner.model.role.Role;
import com.studyplanner.model.user.User;
import com.studyplanner.repository.RefreshTokenRepository;
import com.studyplanner.repository.UserRepository;
import com.studyplanner.service.JwtService;
import com.studyplanner.service.TokenHashService;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final TokenHashService tokenHashService;

    public AuthService(UserRepository userRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       TokenHashService tokenHashService) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.tokenHashService = tokenHashService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.getEmail());

        if (userRepository.existsByEmail(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "Email is already in use");
        }

        User user = new User();
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setFullName(request.getFullname().trim());
        user.setRole(Role.USER);

        userRepository.save(user);

        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = jwtService.generateRefreshToken(user);
        saveRefreshToken(refreshToken, user);

        return buildAuthResponse(user, accessToken, refreshToken);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        String email = normalizeEmail(request.getEmail());
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }

        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = jwtService.generateRefreshToken(user);
        saveRefreshToken(refreshToken, user);

        return buildAuthResponse(user, accessToken, refreshToken);
    }

    @Transactional
    public AuthResponse refresh(RefreshTokenRequest request) {
        String refreshTokenHash = tokenHashService.hash(request.getRefreshToken());
        RefreshToken storedToken = refreshTokenRepository.findByTokenHash(refreshTokenHash)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid refresh token"));

        if (storedToken.isRevoked()
                || storedToken.getExpiryDate().isBefore(LocalDateTime.now())
                || !jwtService.isRefreshTokenValid(request.getRefreshToken())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid refresh token");
        }

        User user = storedToken.getUser();
        storedToken.setRevoked(true);

        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = jwtService.generateRefreshToken(user);
        saveRefreshToken(refreshToken, user);

        return buildAuthResponse(user, accessToken, refreshToken);
    }

    @Transactional
    public void logout(RefreshTokenRequest request) {
        String refreshTokenHash = tokenHashService.hash(request.getRefreshToken());
        RefreshToken storedToken = refreshTokenRepository.findByTokenHash(refreshTokenHash)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid refresh token"));

        storedToken.setRevoked(true);
    }

    private void saveRefreshToken(String token, User user) {
        LocalDateTime expiryDate = LocalDateTime.now().plusNanos(jwtService.getRefreshTokenExpirationMs() * 1_000_000);
        refreshTokenRepository.save(new RefreshToken(tokenHashService.hash(token), user, expiryDate));
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private AuthResponse buildAuthResponse(User user, String accessToken, String refreshToken) {
        UserInfo userInfo = new UserInfo(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getRole().name());

        return new AuthResponse(
                accessToken,
                refreshToken,
                "Bearer",
                jwtService.getAccessTokenExpiresInSeconds(),
                userInfo);
    }
}
