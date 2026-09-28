package com.sufiyan.moviebooking.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.sufiyan.moviebooking.config.JwtProperties;
import com.sufiyan.moviebooking.entity.Role;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-10T10:00:00Z");

    private final SecretKey key = new SecretKeySpec(
            "unit-test-secret-0123456789abcdef-0123456789".getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    private final JwtProperties properties = new JwtProperties("unused", Duration.ofHours(24), "movie-booking");
    private final JwtTokenService service = new JwtTokenService(
            new NimbusJwtEncoder(new ImmutableSecret<>(key)), properties, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void issue_signsTokenWithUserClaimsAndOneDayExpiry() {
        JwtTokenService.IssuedToken token = service.issue(
                new AppUserPrincipal(42L, "alice@example.com", "hash", Role.CUSTOMER));

        // Decode without time validation: this test is about claims, not about "now".
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(jwt -> org.springframework.security.oauth2.core.OAuth2TokenValidatorResult.success());
        Jwt jwt = decoder.decode(token.value());

        assertThat(jwt.getSubject()).isEqualTo("42");
        assertThat(jwt.getClaimAsString("email")).isEqualTo("alice@example.com");
        assertThat(jwt.getClaimAsString("role")).isEqualTo("CUSTOMER");
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("movie-booking");
        assertThat(jwt.getIssuedAt()).isEqualTo(NOW);
        assertThat(jwt.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofHours(24)));
        assertThat(token.expiresAt()).isEqualTo(jwt.getExpiresAt());
        // The password hash must never leak into the token.
        assertThat(jwt.getClaims()).doesNotContainKey("password").doesNotContainValue("hash");
    }
}
