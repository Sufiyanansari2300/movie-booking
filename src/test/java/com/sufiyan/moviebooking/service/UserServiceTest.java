package com.sufiyan.moviebooking.service;

import com.sufiyan.moviebooking.dto.RegisterRequest;
import com.sufiyan.moviebooking.entity.Role;
import com.sufiyan.moviebooking.entity.User;
import com.sufiyan.moviebooking.exception.ConflictException;
import com.sufiyan.moviebooking.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    @Test
    void registerCustomer_normalizesEmailHashesPasswordAndAssignsCustomerRole() {
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);
        when(passwordEncoder.encode("secret123")).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User user = userService.registerCustomer(new RegisterRequest("  Alice ", "  Alice@Example.COM ", "secret123"));

        assertThat(user.getName()).isEqualTo("Alice");
        assertThat(user.getEmail()).isEqualTo("alice@example.com");
        assertThat(user.getPasswordHash()).isEqualTo("hashed");
        assertThat(user.getRole()).isEqualTo(Role.CUSTOMER);
    }

    @Test
    void registerCustomer_rejectsDuplicateEmail() {
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.registerCustomer(
                new RegisterRequest("Alice", "ALICE@example.com", "secret123")))
                .isInstanceOf(ConflictException.class)
                .extracting("errorCode").isEqualTo("EMAIL_ALREADY_REGISTERED");

        verify(userRepository, never()).save(any());
    }
}
