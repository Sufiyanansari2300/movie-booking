package com.sufiyan.moviebooking.controller;

import com.jayway.jsonpath.JsonPath;
import com.sufiyan.moviebooking.entity.City;
import com.sufiyan.moviebooking.entity.Role;
import com.sufiyan.moviebooking.entity.Theater;
import com.sufiyan.moviebooking.repository.CityRepository;
import com.sufiyan.moviebooking.repository.TheaterRepository;
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
class ScreenLayoutIT {

    private static final String STANDARD_LAYOUT = """
            {"sections": [
              {"rows": "A-H", "seatsPerRow": 12, "seatType": "REGULAR"},
              {"rows": "I-J", "seatsPerRow": 10, "seatType": "PREMIUM"}
            ]}
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    @Autowired
    private CityRepository cityRepository;

    @Autowired
    private TheaterRepository theaterRepository;

    private String adminToken;
    private String customerToken;
    private long theaterId;

    @BeforeEach
    void setUp() throws Exception {
        userService.createUser("Admin", "screen-admin@example.com", "password123", Role.ADMIN);
        userService.createUser("Cust", "screen-cust@example.com", "password123", Role.CUSTOMER);
        adminToken = login(mockMvc, "screen-admin@example.com", "password123");
        customerToken = login(mockMvc, "screen-cust@example.com", "password123");
        City city = cityRepository.save(new City("Mumbai", "Maharashtra"));
        theaterId = theaterRepository.save(new Theater(city, "PVR Phoenix", "Lower Parel")).getId();
    }

    @Test
    void createScreenAndLayout_thenBrowse() throws Exception {
        long screenId = createScreen("Audi 1");

        mockMvc.perform(put("/api/admin/screens/{id}/layout", screenId).with(bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(STANDARD_LAYOUT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSeats").value(116))
                .andExpect(jsonPath("$.seatsByType.REGULAR").value(96))
                .andExpect(jsonPath("$.seatsByType.PREMIUM").value(20))
                .andExpect(jsonPath("$.rows", hasSize(10)))
                .andExpect(jsonPath("$.rows[0].row").value("A"))
                .andExpect(jsonPath("$.rows[0].seats", hasSize(12)))
                .andExpect(jsonPath("$.rows[0].seats[0].label").value("A1"))
                .andExpect(jsonPath("$.rows[9].row").value("J"))
                .andExpect(jsonPath("$.rows[9].seatType").value("PREMIUM"));

        // Public screen list shows seat counts
        mockMvc.perform(get("/api/theaters/{id}/screens", theaterId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("Audi 1"))
                .andExpect(jsonPath("$[0].totalSeats").value(116));
    }

    @Test
    void replacingLayout_swapsAllSeats() throws Exception {
        long screenId = createScreen("Audi 1");
        mockMvc.perform(put("/api/admin/screens/{id}/layout", screenId).with(bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(STANDARD_LAYOUT))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/admin/screens/{id}/layout", screenId).with(bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sections": [{"rows": "A-B", "seatsPerRow": 5, "seatType": "PREMIUM"}]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSeats").value(10));

        mockMvc.perform(get("/api/admin/screens/{id}/layout", screenId).with(bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSeats").value(10))
                .andExpect(jsonPath("$.seatsByType.REGULAR").doesNotExist());
    }

    @Test
    void invalidLayout_returns400() throws Exception {
        long screenId = createScreen("Audi 1");

        mockMvc.perform(put("/api/admin/screens/{id}/layout", screenId).with(bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sections": [
                                  {"rows": "A-E", "seatsPerRow": 10, "seatType": "REGULAR"},
                                  {"rows": "C", "seatsPerRow": 10, "seatType": "PREMIUM"}
                                ]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_LAYOUT"));

        mockMvc.perform(put("/api/admin/screens/{id}/layout", screenId).with(bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sections": [{"rows": "A", "seatsPerRow": 0, "seatType": "VIP"}]}
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put("/api/admin/screens/{id}/layout", screenId).with(bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sections": []}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"));
    }

    @Test
    void duplicateScreenName_returns409() throws Exception {
        createScreen("Audi 1");

        mockMvc.perform(post("/api/admin/theaters/{id}/screens", theaterId).with(bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "audi 1"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("SCREEN_ALREADY_EXISTS"));
    }

    @Test
    void deleteTheater_blockedWhileScreensExist_thenAllowed() throws Exception {
        long screenId = createScreen("Audi 1");
        mockMvc.perform(put("/api/admin/screens/{id}/layout", screenId).with(bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(STANDARD_LAYOUT))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/admin/theaters/{id}", theaterId).with(bearer(adminToken)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("THEATER_HAS_SCREENS"));

        // Deleting the screen removes its seats too
        mockMvc.perform(delete("/api/admin/screens/{id}", screenId).with(bearer(adminToken)))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/admin/theaters/{id}", theaterId).with(bearer(adminToken)))
                .andExpect(status().isNoContent());
    }

    @Test
    void customerCannotManageScreens() throws Exception {
        mockMvc.perform(post("/api/admin/theaters/{id}/screens", theaterId).with(bearer(customerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Audi 9"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void unknownScreen_returns404() throws Exception {
        mockMvc.perform(get("/api/admin/screens/{id}/layout", 999999).with(bearer(adminToken)))
                .andExpect(status().isNotFound());
    }

    private long createScreen(String name) throws Exception {
        String body = mockMvc.perform(post("/api/admin/theaters/{id}/screens", theaterId).with(bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"%s\"}".formatted(name)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalSeats").value(0))
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }
}
