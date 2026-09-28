package com.sufiyan.moviebooking.controller;

import com.jayway.jsonpath.JsonPath;
import com.sufiyan.moviebooking.entity.Role;
import com.sufiyan.moviebooking.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static com.sufiyan.moviebooking.support.AuthTestSupport.bearer;
import static com.sufiyan.moviebooking.support.AuthTestSupport.login;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CityTheaterIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    private String adminToken;
    private String customerToken;

    @BeforeEach
    void setUp() throws Exception {
        userService.createUser("Admin", "catalog-admin@example.com", "password123", Role.ADMIN);
        userService.createUser("Cust", "catalog-cust@example.com", "password123", Role.CUSTOMER);
        adminToken = login(mockMvc, "catalog-admin@example.com", "password123");
        customerToken = login(mockMvc, "catalog-cust@example.com", "password123");
    }

    @Test
    void adminCreatesCityAndTheater_publicCanBrowse() throws Exception {
        long cityId = createCity("Mumbai", "Maharashtra");
        createCity("Bengaluru", "Karnataka");
        long theaterId = createTheater(cityId, "PVR Phoenix", "Lower Parel");

        // Public endpoints need no token
        mockMvc.perform(get("/api/cities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].name").value("Bengaluru")); // sorted by name

        mockMvc.perform(get("/api/cities/{id}/theaters", cityId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("PVR Phoenix"))
                .andExpect(jsonPath("$[0].city.name").value("Mumbai"));

        mockMvc.perform(get("/api/theaters/{id}", theaterId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.address").value("Lower Parel"));
    }

    @Test
    void customerCannotCreateCity() throws Exception {
        mockMvc.perform(post("/api/admin/cities").with(bearer(customerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Pune", "state": "Maharashtra"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymousCannotCreateCity() throws Exception {
        mockMvc.perform(post("/api/admin/cities")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Pune", "state": "Maharashtra"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createCity_validationAndDuplicates() throws Exception {
        mockMvc.perform(post("/api/admin/cities").with(bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "", "state": ""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors", hasSize(2)));

        createCity("Pune", "Maharashtra");
        mockMvc.perform(post("/api/admin/cities").with(bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "PUNE", "state": "Maharashtra"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CITY_ALREADY_EXISTS"));
    }

    @Test
    void updateCity() throws Exception {
        long cityId = createCity("Bombay", "Maharashtra");

        mockMvc.perform(put("/api/admin/cities/{id}", cityId).with(bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Mumbai", "state": "Maharashtra"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Mumbai"));
    }

    @Test
    void deleteCity_blockedWhileTheatersExist() throws Exception {
        long cityId = createCity("Mumbai", "Maharashtra");
        long theaterId = createTheater(cityId, "PVR Phoenix", "Lower Parel");

        mockMvc.perform(delete("/api/admin/cities/{id}", cityId).with(bearer(adminToken)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CITY_HAS_THEATERS"));

        mockMvc.perform(delete("/api/admin/theaters/{id}", theaterId).with(bearer(adminToken)))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/admin/cities/{id}", cityId).with(bearer(adminToken)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/cities/{id}", cityId))
                .andExpect(status().isNotFound());
    }

    @Test
    void createTheater_unknownCity_returns404() throws Exception {
        mockMvc.perform(post("/api/admin/theaters").with(bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"cityId": 999999, "name": "Ghost", "address": "Nowhere"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }

    @Test
    void createTheater_duplicateNameInSameCity_returns409() throws Exception {
        long cityId = createCity("Mumbai", "Maharashtra");
        createTheater(cityId, "PVR Phoenix", "Lower Parel");

        mockMvc.perform(post("/api/admin/theaters").with(bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"cityId": %d, "name": "pvr phoenix", "address": "Elsewhere"}
                                """.formatted(cityId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("THEATER_ALREADY_EXISTS"));
    }

    @Test
    void invalidPathId_returns400() throws Exception {
        mockMvc.perform(get("/api/cities/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_PARAMETER"));
    }

    private long createCity(String name, String state) throws Exception {
        String body = mockMvc.perform(post("/api/admin/cities").with(bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"%s\", \"state\": \"%s\"}".formatted(name, state)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private long createTheater(long cityId, String name, String address) throws Exception {
        String body = mockMvc.perform(post("/api/admin/theaters").with(bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cityId\": %d, \"name\": \"%s\", \"address\": \"%s\"}".formatted(cityId, name, address)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }
}
