package com.sufiyan.moviebooking.security;

import com.sufiyan.moviebooking.config.JwtProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;

/**
 * Issues signed access tokens. Claims: {@code sub} = user id, {@code email}, {@code role}.
 */
@Service
@RequiredArgsConstructor
public class JwtTokenService {

    public static final String CLAIM_EMAIL = "email";
    public static final String CLAIM_ROLE = "role";

    private final JwtEncoder jwtEncoder;
    private final JwtProperties properties;
    private final Clock clock;

    public IssuedToken issue(AppUserPrincipal user) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(properties.expiration());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .subject(String.valueOf(user.id()))
                .issuedAt(now)
                .expiresAt(expiresAt)
                .claim(CLAIM_EMAIL, user.email())
                .claim(CLAIM_ROLE, user.role().name())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedToken(token, now, expiresAt);
    }

    public record IssuedToken(String value, Instant issuedAt, Instant expiresAt) {
    }
}
