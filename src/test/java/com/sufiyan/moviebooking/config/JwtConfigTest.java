package com.sufiyan.moviebooking.config;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtConfigTest {

    private final JwtConfig config = new JwtConfig();

    @Test
    void missingOrShortSecret_failsFastAtStartup() {
        assertThatThrownBy(() -> config.jwtSigningKey(new JwtProperties(null, Duration.ofHours(24), "movie-booking")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("app.jwt.secret");
        assertThatThrownBy(() -> config.jwtSigningKey(new JwtProperties("too-short", Duration.ofHours(24), "movie-booking")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("32 bytes");
    }

    @Test
    void secretOf32BytesOrMore_isAccepted() {
        assertThat(config.jwtSigningKey(new JwtProperties("x".repeat(32), Duration.ofHours(24), "movie-booking"))
                .getAlgorithm()).isEqualTo("HmacSHA256");
    }
}
