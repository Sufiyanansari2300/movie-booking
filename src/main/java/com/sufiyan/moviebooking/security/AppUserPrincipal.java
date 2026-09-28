package com.sufiyan.moviebooking.security;

import com.sufiyan.moviebooking.entity.Role;
import com.sufiyan.moviebooking.entity.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Authenticated user as seen by Spring Security. Carries the user id so controllers
 * can resolve the caller via {@code @AuthenticationPrincipal} without another lookup.
 */
public record AppUserPrincipal(Long id, String email, String passwordHash, Role role) implements UserDetails {

    public static AppUserPrincipal from(User user) {
        return new AppUserPrincipal(user.getId(), user.getEmail(), user.getPasswordHash(), user.getRole());
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }
}
