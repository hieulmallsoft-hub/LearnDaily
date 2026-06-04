package com.studyplanner.service;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.studyplanner.model.user.User;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.security.Keys;

@Service
public class JwtService {
    private final SecretKey signingKey;
    private final long accessTokenExpirationMs;
    private final long refreshTokenExpirationMs;

    public JwtService(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-expiration-ms}") long accessTokenExpirationMs,
            @Value("${jwt.refresh-token-expiration-ms}") long refreshTokenExpirationMs) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpirationMs = accessTokenExpirationMs;
        this.refreshTokenExpirationMs = refreshTokenExpirationMs;
    }

    public String generateAccessToken(User user) {
        return generateToken(user, accessTokenExpirationMs, Map.of("type", "access"));
    }

    public String generateRefreshToken(User user) {
        return generateToken(user, refreshTokenExpirationMs, Map.of("type", "refresh"));
    }

    public long getAccessTokenExpiresInSeconds() {
        return accessTokenExpirationMs / 1000;
    }

    public long getRefreshTokenExpirationMs() {
        return refreshTokenExpirationMs;
    }

    public boolean isRefreshTokenValid(String token) {
        return "refresh".equals(extractTokenType(token)) && extractExpiration(token).after(new Date());
    }

    public boolean isAccessTokenValid(String token) {
        return "access".equals(extractTokenType(token)) && extractExpiration(token).after(new Date());
    }

    public String extractSubject(String token) {
        return extractAllClaims(token).getSubject();
    }

    private String extractTokenType(String token) {
        return extractAllClaims(token).get("type", String.class);
    }

    private Date extractExpiration(String token) {
        return extractAllClaims(token).getExpiration();
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private String generateToken(User user, long expirationMs, Map<String, Object> extraClaims) {
        Date now = new Date();
        Date expiresAt = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
                .subject(user.getEmail())
                .claims(extraClaims)
                .claim("userId", user.getId())
                .claim("role", user.getRole())
                .issuedAt(now)
                .expiration(expiresAt)
                .signWith(signingKey)
                .compact();
    }
}
