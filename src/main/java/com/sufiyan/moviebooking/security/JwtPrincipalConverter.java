package com.sufiyan.moviebooking.security;

import com.sufiyan.moviebooking.entity.Role;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.stereotype.Component;

/**
 * Turns a validated JWT into an authentication whose principal is {@link AppUserPrincipal},
 * so controllers use {@code @AuthenticationPrincipal AppUserPrincipal} regardless of how the user logged in.
 */
@Component
public class JwtPrincipalConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        try {
            Long userId = Long.valueOf(jwt.getSubject());
            String email = jwt.getClaimAsString(JwtTokenService.CLAIM_EMAIL);
            Role role = Role.valueOf(jwt.getClaimAsString(JwtTokenService.CLAIM_ROLE));
            AppUserPrincipal principal = new AppUserPrincipal(userId, email, null, role);
            return UsernamePasswordAuthenticationToken.authenticated(principal, jwt, principal.getAuthorities());
        } catch (RuntimeException e) {
            throw new InvalidBearerTokenException("Token is missing required claims");
        }
    }
}
