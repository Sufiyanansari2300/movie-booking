package com.sufiyan.moviebooking.service;

import com.sufiyan.moviebooking.dto.RegisterRequest;
import com.sufiyan.moviebooking.entity.Role;
import com.sufiyan.moviebooking.entity.User;
import com.sufiyan.moviebooking.exception.ConflictException;
import com.sufiyan.moviebooking.exception.ResourceNotFoundException;
import com.sufiyan.moviebooking.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /** Public self-registration always creates a CUSTOMER; admins are provisioned separately. */
    @Transactional
    public User registerCustomer(RegisterRequest request) {
        return createUser(request.name(), request.email(), request.password(), Role.CUSTOMER);
    }

    @Transactional
    public User createUser(String name, String email, String rawPassword, Role role) {
        String normalizedEmail = normalizeEmail(email);
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new ConflictException("EMAIL_ALREADY_REGISTERED", "Email is already registered: " + normalizedEmail);
        }
        User user = new User(name.trim(), normalizedEmail, passwordEncoder.encode(rawPassword), role);
        return userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public User getById(Long id) {
        return userRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("User", id));
    }

    public static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
