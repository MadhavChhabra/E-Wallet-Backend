package com.ewallet.security;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static com.ewallet.common.Constants.*;

@Slf4j(topic = "JwtUtils")
@Component
public class JwtUtils {

    @Value("${app.security.jwtSecret}")
    private String jwtSecret;

    @Value("${app.security.refreshTokenSecret}")
    private String refreshTokenSecret;

    @Value("${app.security.jwtExpirationMs}")
    private int jwtExpirationMs;

    @Value("${app.security.refreshTokenExpirationMs}")
    private long refreshTokenExpirationMs;

    public String generateJwtToken(Authentication authentication) {
        UserDetailsImpl userPrincipal = (UserDetailsImpl) authentication.getPrincipal();
        return buildToken(userPrincipal.getUsername(), jwtExpirationMs, getSigningKey(jwtSecret));
    }

    public String generateRefreshToken(Authentication authentication) {
        UserDetailsImpl userPrincipal = (UserDetailsImpl) authentication.getPrincipal();
        return buildToken(userPrincipal.getUsername(), refreshTokenExpirationMs, getSigningKey(refreshTokenSecret));
    }

    private String buildToken(String subject, long expirationMs, SecretKey key) {
        Date now = new Date();
        return Jwts.builder()
                .subject(subject)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expirationMs))
                .signWith(key)
                .compact();
    }

    public String getUsernameFromJwtToken(String token) {
        return parseClaims(token, getSigningKey(jwtSecret)).getSubject();
    }

    public String getUsernameFromRefreshToken(String token) {
        return parseClaims(token, getSigningKey(refreshTokenSecret)).getSubject();
    }

    public boolean validateJwtToken(String authToken) {
        return isTokenValid(authToken, jwtSecret);
    }

    public boolean validateRefreshToken(String refreshToken) {
        return isTokenValid(refreshToken, refreshTokenSecret);
    }

    private boolean isTokenValid(String authToken, String secret) {
        try {
            parseClaims(authToken, getSigningKey(secret));
            return true;
        } catch (SignatureException e) {
            log.error(INVALID_JWT_SIGN, e.getMessage());
        } catch (MalformedJwtException e) {
            log.error(INVALID_JWT_TOKEN, e.getMessage());
        } catch (ExpiredJwtException e) {
            log.error(JWT_EXPIRED, e.getMessage());
        } catch (UnsupportedJwtException e) {
            log.error(JWT_UNSUPPORTED, e.getMessage());
        } catch (IllegalArgumentException e) {
            log.error(JWT_EMPTY, e.getMessage());
        }
        return false;
    }

    private io.jsonwebtoken.Claims parseClaims(String token, SecretKey key) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey getSigningKey(String secret) {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }
}
