package com.sufiyan.moviebooking.config;

import com.sufiyan.moviebooking.entity.Role;
import com.sufiyan.moviebooking.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AdminBootstrapIT {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AdminBootstrap adminBootstrap;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void createsAdminOnStartup_andAdminCanLogIn() throws Exception {
        assertThat(userRepository.findByEmail("admin@test.local"))
                .get().extracting("role").isEqualTo(Role.ADMIN);

        mockMvc.perform(get("/api/auth/me").with(httpBasic("admin@test.local", "admin-test-password")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void runningAgain_doesNotCreateDuplicate() {
        long before = userRepository.count();

        adminBootstrap.run(new DefaultApplicationArguments());

        assertThat(userRepository.count()).isEqualTo(before);
    }
}
