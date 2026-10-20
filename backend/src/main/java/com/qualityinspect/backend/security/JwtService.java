package com.qualityinspect.backend.security;

import com.qualityinspect.backend.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;

/**
 * Access tokens: signed JWTs (HS256). Refresh tokens: opaque random values stored
 * only as SHA-256 hashes in the database (rotation + revocation supported).
 */
@Service
public class JwtService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final AppProperties props;

    public JwtService(AppProperties props) {
        this.props = props;
        byte[] secret = props.getJwt().getSecret().getBytes(StandardCharsets.UTF_8);
        if (secret.length < 32) {
            throw new IllegalStateException("APP_JWT_SECRET must be at least 32 characters");
        }
        this.key = Keys.hmacShaKeyFor(secret);
    }

    private final SecretKey key;

    public String createAccessToken(AuthPrincipal principal) {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(props.getJwt().getAccessMinutes() * 60);
        return Jwts.builder()
                .subject(principal.getUsername())
                .claim("uid", principal.getId())
                .claim("roles", List.copyOf(principal.getRoles()))
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    public long accessTtlSeconds() {
        return props.getJwt().getAccessMinutes() * 60;
    }

    public long refreshTtlSeconds() {
        return props.getJwt().getRefreshDays() * 24 * 3600;
    }

    /** Parses a valid access token; throws JwtException when invalid/expired/tampered. */
    public Claims parseAccessToken(String token) {
        return Jwts.parser().verifyWith(key).build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isMalformed(String token) {
        try {
            parseAccessToken(token);
            return false;
        } catch (JwtException | IllegalArgumentException ex) {
            return true;
        }
    }

    /** New opaque refresh token value (never stored in plaintext). */
    public String newRefreshTokenValue() {
        byte[] bytes = new byte[48];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    public String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}
