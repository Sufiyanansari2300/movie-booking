package com.sufiyan.moviebooking.config;

import com.sufiyan.moviebooking.entity.Role;
import com.sufiyan.moviebooking.repository.UserRepository;
import com.sufiyan.moviebooking.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Creates the initial admin on startup if it does not exist yet. Admins cannot self-register.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(AdminProperties.class)
public class AdminBootstrap implements ApplicationRunner {

    private final AdminProperties properties;
    private final UserRepository userRepository;
    private final UserService userService;

    @Override
    public void run(ApplicationArguments args) {
        if (!StringUtils.hasText(properties.email()) || !StringUtils.hasText(properties.password())) {
            log.warn("No bootstrap admin configured (set app.admin.email and app.admin.password / ADMIN_PASSWORD)");
            return;
        }
        String email = UserService.normalizeEmail(properties.email());
        if (userRepository.existsByEmail(email)) {
            log.info("Bootstrap admin {} already exists", email);
            return;
        }
        String name = StringUtils.hasText(properties.name()) ? properties.name() : "Administrator";
        userService.createUser(name, email, properties.password(), Role.ADMIN);
        log.info("Created bootstrap admin {}", email);
    }
}
