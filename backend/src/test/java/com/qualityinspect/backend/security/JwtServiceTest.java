package com.qualityinspect.backend.security;

import com.qualityinspect.backend.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        AppProperties props = new AppProperties();
        props.getJwt().setSecret("test_secret_key_that_is_long_enough_for_hs256_32bytes");
        props.getJwt().setAccessMinutes(30);
        props.getJwt().setRefreshDays(7);
        jwtService = new JwtService(props);
    }

    private AuthPrincipal principal() {
        return new AuthPrincipal(42L, "tester", "hash", true, Set.of("QUALITY_MANAGER"));
    }

    @Test
    void createAndParse_roundTrip() {
        String token = jwtService.createAccessToken(principal());
        assertNotNull(token);

        Claims claims = jwtService.parseAccessToken(token);
        assertEquals("tester", claims.getSubject());
        assertEquals(42L, claims.get("uid", Long.class));
        assertTrue(claims.get("roles", java.util.List.class).contains("QUALITY_MANAGER"));
    }

    @Test
    void parseAccessToken_rejectsGarbage() {
        assertTrue(jwtService.isMalformed("not.a.token"));
        assertThrows(JwtException.class, () -> jwtService.parseAccessToken("not.a.token"));
    }

    @Test
    void isMalformed_falseForValidToken() {
        assertFalse(jwtService.isMalformed(jwtService.createAccessToken(principal())));
    }

    @Test
    void newRefreshTokenValue_isUniqueOpaqueHex() {
        String a = jwtService.newRefreshTokenValue();
        String b = jwtService.newRefreshTokenValue();
        assertNotEquals(a, b);
        assertEquals(96, a.length());
        assertTrue(a.matches("[0-9a-f]+"));
    }

    @Test
    void sha256_isStable() {
        String h1 = jwtService.sha256("hello");
        String h2 = jwtService.sha256("hello");
        assertEquals(h1, h2);
        assertEquals(64, h1.length());
    }

    @Test
    void accessAndRefreshTtl_derivedFromConfig() {
        assertEquals(1800, jwtService.accessTtlSeconds());
        assertEquals(7 * 24 * 3600, jwtService.refreshTtlSeconds());
    }
}
