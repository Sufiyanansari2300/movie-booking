package com.sufiyan.moviebooking.config;

import com.sufiyan.moviebooking.entity.Role;
import com.sufiyan.moviebooking.repository.UserRepository;
import com.sufiyan.moviebooking.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class AdminBootstrapTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final UserService userService = mock(UserService.class);

    @Test
    void withoutAPassword_noAdminIsCreated() {
        new AdminBootstrap(new AdminProperties("Admin", "admin@example.com", " "), userRepository, userService)
                .run(new DefaultApplicationArguments());
        new AdminBootstrap(new AdminProperties("Admin", null, "secret-pass"), userRepository, userService)
                .run(new DefaultApplicationArguments());

        verify(userService, never()).createUser(any(), any(), any(), any());
    }

    @Test
    void missingName_defaultsToAdministrator_andEmailIsNormalized() {
        new AdminBootstrap(new AdminProperties(null, " Root@Example.COM ", "secret-pass"), userRepository, userService)
                .run(new DefaultApplicationArguments());

        verify(userService).createUser("Administrator", "root@example.com", "secret-pass", Role.ADMIN);
    }
}
