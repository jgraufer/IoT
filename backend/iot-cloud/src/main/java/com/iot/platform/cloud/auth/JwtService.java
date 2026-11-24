package com.iot.platform.cloud.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SecurityException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.UUID;

@Slf4j
@Component
public class JwtService {

    public static final String CLAIM_TYP = "typ";
    public static final String TYP_ACCESS = "access";
    public static final String TYP_REFRESH = "refresh";

    private static final Duration ACCESS_TTL = Duration.ofMinutes(15);
    private static final Duration REFRESH_TTL = Duration.ofDays(7);

    private final SecretKey secretKey;

    public JwtService(@Value("${jwt.secret}") String secret) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccessToken(UUID userId) {
        Date now = new Date();
        return Jwts.builder()
                .subject(userId.toString())
                .claim(CLAIM_TYP, TYP_ACCESS)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + ACCESS_TTL.toMillis()))
                .signWith(secretKey)
                .compact();
    }

    public String generateRefreshToken(UUID userId) {
        Date now = new Date();
        return Jwts.builder()
                .subject(userId.toString())
                .id(UUID.randomUUID().toString())
                .claim(CLAIM_TYP, TYP_REFRESH)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + REFRESH_TTL.toMillis()))
                .signWith(secretKey)
                .compact();
    }

    public Duration refreshTokenTtl() {
        return REFRESH_TTL;
    }

    public boolean validateToken(String token) {
        try {
            getClaims(token);
            return true;
        } catch (SecurityException | MalformedJwtException e) {
            log.debug("JWT inválido (firma o formato): {}", e.getMessage());
        } catch (ExpiredJwtException e) {
            log.debug("JWT expirado");
        } catch (UnsupportedJwtException e) {
            log.debug("JWT no soportado: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            log.debug("JWT vacío");
        }
        return false;
    }

    public UUID getUserIdFromToken(String token) {
        return UUID.fromString(getClaims(token).getSubject());
    }

    public String getTypFromToken(String token) {
        return getClaims(token).get(CLAIM_TYP, String.class);
    }

    private Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
