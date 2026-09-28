package com.sufiyan.moviebooking.controller;

import com.jayway.jsonpath.JsonPath;
import com.sufiyan.moviebooking.dto.SeatLayoutRequest;
import com.sufiyan.moviebooking.dto.SeatLayoutRequest.Section;
import com.sufiyan.moviebooking.entity.City;
import com.sufiyan.moviebooking.entity.Movie;
import com.sufiyan.moviebooking.entity.Role;
import com.sufiyan.moviebooking.entity.Screen;
import com.sufiyan.moviebooking.entity.SeatType;
import com.sufiyan.moviebooking.entity.Show;
import com.sufiyan.moviebooking.entity.ShowSeatStatus;
import com.sufiyan.moviebooking.entity.Theater;
import com.sufiyan.moviebooking.repository.CityRepository;
import com.sufiyan.moviebooking.repository.MovieRepository;
import com.sufiyan.moviebooking.repository.ScreenRepository;
import com.sufiyan.moviebooking.repository.ShowRepository;
import com.sufiyan.moviebooking.repository.ShowSeatRepository;
import com.sufiyan.moviebooking.repository.TheaterRepository;
import com.sufiyan.moviebooking.service.ScreenService;
import com.sufiyan.moviebooking.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

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
class ShowIT {

    private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");
    private static final String PRICES = "{\"REGULAR\": 200.00, \"PREMIUM\": 350.00}";

    @Autowired private MockMvc mockMvc;
    @Autowired private UserService userService;
    @Autowired private CityRepository cityRepository;
    @Autowired private TheaterRepository theaterRepository;
    @Autowired private ScreenRepository screenRepository;
    @Autowired private MovieRepository movieRepository;
    @Autowired private ShowRepository showRepository;
    @Autowired private ShowSeatRepository showSeatRepository;
    @Autowired private ScreenService screenService;

    private String adminToken;
    private String customerToken;
    private long cityId;
    private long screenId;
    private long otherScreenId;
    private long movieId;
    private Movie movie;
    private OffsetDateTime tomorrow6pm;

    @BeforeEach
    void setUp() throws Exception {
        userService.createUser("Admin", "show-admin@example.com", "password123", Role.ADMIN);
        userService.createUser("Cust", "show-cust@example.com", "password123", Role.CUSTOMER);
        adminToken = login(mockMvc, "show-admin@example.com", "password123");
        customerToken = login(mockMvc, "show-cust@example.com", "password123");

        City city = cityRepository.save(new City("Pune", "Maharashtra"));
        cityId = city.getId();
        Theater theater = theaterRepository.save(new Theater(city, "Riverside Cinemas", "Koregaon Park"));
        screenId = screenWithLayout(theater, "Audi 1");
        otherScreenId = screenWithLayout(theater, "Audi 2");
        movie = movieRepository.save(new Movie("Inception", null, "English", "Sci-Fi", 148, "UA", null));
        movieId = movie.getId();
        tomorrow6pm = LocalDate.now(ZONE).plusDays(1).atTime(18, 0).atZone(ZONE).toOffsetDateTime();
    }

    @Test
    void createShow_computesEndTimeAndOpensAllSeats() throws Exception {
        createShow(screenId, tomorrow6pm, PRICES)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.movie.title").value("Inception"))
                .andExpect(jsonPath("$.theater.cityName").value("Pune"))
                .andExpect(jsonPath("$.screen.name").value("Audi 1"))
                .andExpect(jsonPath("$.startTime").value(iso(tomorrow6pm)))
                // 148 min movie + 15 min cleanup buffer
                .andExpect(jsonPath("$.endTime").value(iso(tomorrow6pm.plusMinutes(163))))
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.prices.REGULAR").value(200.00))
                .andExpect(jsonPath("$.prices.PREMIUM").value(350.00))
                .andExpect(jsonPath("$.availableSeats").value(116));
    }

    @Test
    void seatMap_listsEverySeatWithStatusAndPrice() throws Exception {
        long showId = idOf(createShow(screenId, tomorrow6pm, PRICES));

        mockMvc.perform(get("/api/shows/{id}/seats", showId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.AVAILABLE").value(116))
                .andExpect(jsonPath("$.summary.BOOKED").value(0))
                .andExpect(jsonPath("$.rows", hasSize(10)))
                .andExpect(jsonPath("$.rows[0].row").value("A"))
                .andExpect(jsonPath("$.rows[0].price").value(200.00))
                .andExpect(jsonPath("$.rows[0].seats", hasSize(12)))
                .andExpect(jsonPath("$.rows[0].seats[0].showSeatId").isNumber())
                .andExpect(jsonPath("$.rows[0].seats[0].label").value("A1"))
                .andExpect(jsonPath("$.rows[0].seats[0].status").value("AVAILABLE"))
                .andExpect(jsonPath("$.rows[9].seatType").value("PREMIUM"))
                .andExpect(jsonPath("$.rows[9].price").value(350.00));
    }

    @Test
    void overlappingShowOnSameScreen_isRejected_butBackToBackAndOtherScreensAreFine() throws Exception {
        createShow(screenId, tomorrow6pm, PRICES).andExpect(status().isCreated());

        // Starts while the first show (18:00-20:43) is still running
        createShow(screenId, tomorrow6pm.plusHours(2), PRICES)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("SHOW_OVERLAP"));
        // Ends after the first one started
        createShow(screenId, tomorrow6pm.minusHours(1), PRICES)
                .andExpect(status().isConflict());
        // Starts exactly when the first show (incl. cleanup) ends
        createShow(screenId, tomorrow6pm.plusMinutes(163), PRICES).andExpect(status().isCreated());
        // Same time on a different screen
        createShow(otherScreenId, tomorrow6pm, PRICES).andExpect(status().isCreated());
    }

    @Test
    void invalidShows_areRejected() throws Exception {
        createShow(screenId, OffsetDateTime.now(ZONE).minusHours(1), PRICES)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("SHOW_IN_PAST"));

        createShow(screenId, tomorrow6pm, "{\"REGULAR\": 200.00}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("MISSING_PRICE"));

        createShow(screenId, tomorrow6pm, "{\"REGULAR\": -5, \"PREMIUM\": 350.123}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"));

        long emptyScreen = screenRepository.save(new Screen(theaterRepository.findAll().getFirst(), "Empty")).getId();
        createShow(emptyScreen, tomorrow6pm, PRICES)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("SCREEN_HAS_NO_SEATS"));

        mockMvc.perform(post("/api/admin/shows").with(bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(999999L, screenId, tomorrow6pm, PRICES)))
                .andExpect(status().isNotFound());
    }

    @Test
    void publicSearch_filtersByCityMovieAndDate_andHidesPastShows() throws Exception {
        createShow(screenId, tomorrow6pm, PRICES).andExpect(status().isCreated());
        createShow(otherScreenId, tomorrow6pm.plusDays(1), PRICES).andExpect(status().isCreated());
        // A show that already started: never listed
        Instant past = Instant.now().minus(Duration.ofHours(1));
        showRepository.save(new Show(movie, screenRepository.getReferenceById(screenId), past, past.plusSeconds(3600)));

        mockMvc.perform(get("/api/shows").param("cityId", String.valueOf(cityId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].startTime").value(iso(tomorrow6pm))); // sorted by start

        mockMvc.perform(get("/api/shows")
                        .param("movieId", String.valueOf(movieId))
                        .param("date", tomorrow6pm.toLocalDate().toString()))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].screen.name").value("Audi 1"));

        mockMvc.perform(get("/api/shows").param("cityId", "999999"))
                .andExpect(jsonPath("$.totalElements").value(0));

        mockMvc.perform(get("/api/shows").param("sort", "movie"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_PARAMETER"));
    }

    @Test
    void layoutScreenAndMovie_areFrozenOnceShowsExist() throws Exception {
        long showId = idOf(createShow(screenId, tomorrow6pm, PRICES));

        mockMvc.perform(put("/api/admin/screens/{id}/layout", screenId).with(bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sections\": [{\"rows\": \"A\", \"seatsPerRow\": 5, \"seatType\": \"REGULAR\"}]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("SCREEN_HAS_SHOWS"));
        mockMvc.perform(delete("/api/admin/screens/{id}", screenId).with(bearer(adminToken)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("SCREEN_HAS_SHOWS"));
        mockMvc.perform(delete("/api/admin/movies/{id}", movieId).with(bearer(adminToken)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("MOVIE_HAS_SHOWS"));

        // Once the show is gone the layout can change again
        mockMvc.perform(delete("/api/admin/shows/{id}", showId).with(bearer(adminToken)))
                .andExpect(status().isNoContent());
        mockMvc.perform(put("/api/admin/screens/{id}/layout", screenId).with(bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sections\": [{\"rows\": \"A\", \"seatsPerRow\": 5, \"seatType\": \"REGULAR\"}]}"))
                .andExpect(status().isOk());
    }

    @Test
    void updatePrices() throws Exception {
        long showId = idOf(createShow(screenId, tomorrow6pm, PRICES));

        mockMvc.perform(put("/api/admin/shows/{id}/prices", showId).with(bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prices\": {\"REGULAR\": 250.50, \"PREMIUM\": 400}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.prices.REGULAR").value(250.50))
                .andExpect(jsonPath("$.prices.PREMIUM").value(400));

        mockMvc.perform(put("/api/admin/shows/{id}/prices", showId).with(bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prices\": {\"PREMIUM\": 400}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("MISSING_PRICE"));
    }

    @Test
    void deleteShow_blockedWhenSeatsAreHeldOrBooked() throws Exception {
        long showId = idOf(createShow(screenId, tomorrow6pm, PRICES));
        showSeatRepository.findSeatMap(showId).getFirst().setStatus(ShowSeatStatus.BOOKED);

        mockMvc.perform(delete("/api/admin/shows/{id}", showId).with(bearer(adminToken)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("SHOW_HAS_BOOKINGS"));
    }

    @Test
    void customerCannotScheduleShows_butCanBrowseWithoutToken() throws Exception {
        mockMvc.perform(post("/api/admin/shows").with(bearer(customerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(movieId, screenId, tomorrow6pm, PRICES)))
                .andExpect(status().isForbidden());

        long showId = idOf(createShow(screenId, tomorrow6pm, PRICES));
        mockMvc.perform(get("/api/shows/{id}", showId)).andExpect(status().isOk());
        mockMvc.perform(get("/api/shows/{id}/seats", showId)).andExpect(status().isOk());
    }

    private long screenWithLayout(Theater theater, String name) {
        Screen screen = screenRepository.save(new Screen(theater, name));
        screenService.replaceLayout(screen.getId(), new SeatLayoutRequest(List.of(
                new Section("A-H", 12, SeatType.REGULAR), new Section("I-J", 10, SeatType.PREMIUM))));
        return screen.getId();
    }

    private ResultActions createShow(long screen, OffsetDateTime start, String prices) throws Exception {
        return mockMvc.perform(post("/api/admin/shows").with(bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(movieId, screen, start, prices)));
    }

    private static String body(long movie, long screen, OffsetDateTime start, String prices) {
        return "{\"movieId\": %d, \"screenId\": %d, \"startTime\": \"%s\", \"prices\": %s}"
                .formatted(movie, screen, start, prices);
    }

    private static String iso(OffsetDateTime time) {
        return time.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX"));
    }

    private static long idOf(ResultActions result) throws Exception {
        String json = result.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }
}
