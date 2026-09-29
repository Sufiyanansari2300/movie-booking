package com.sufiyan.moviebooking.controller;

import com.jayway.jsonpath.JsonPath;
import com.sufiyan.moviebooking.entity.Role;
import com.sufiyan.moviebooking.entity.User;
import com.sufiyan.moviebooking.support.MutableClock;
import com.sufiyan.moviebooking.support.MutableClockConfig;
import com.sufiyan.moviebooking.support.TestData;
import com.sufiyan.moviebooking.support.TestData.ShowFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.sufiyan.moviebooking.support.AuthTestSupport.bearer;
import static com.sufiyan.moviebooking.support.AuthTestSupport.login;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(MutableClockConfig.class)
@Transactional
class BookingHistoryIT {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    @Autowired private MockMvc mockMvc;
    @Autowired private ApplicationContext ctx;
    @Autowired private MutableClock clock;

    private TestData data;
    private ShowFixture tomorrow;
    private ShowFixture nextWeek;
    private User aliceUser;
    private String alice;
    private String bob;
    private String admin;

    @BeforeEach
    void setUp() throws Exception {
        clock.reset();
        data = new TestData(ctx);
        tomorrow = data.show(); // tomorrow 18:00
        nextWeek = data.show(LocalDate.now(IST).plusDays(7).atTime(18, 0).atZone(IST).toOffsetDateTime());
        aliceUser = data.user(Role.CUSTOMER);
        alice = login(mockMvc, aliceUser.getEmail(), TestData.PASSWORD);
        bob = login(mockMvc, data.user(Role.CUSTOMER).getEmail(), TestData.PASSWORD);
        admin = login(mockMvc, data.user(Role.ADMIN).getEmail(), TestData.PASSWORD);
    }

    @Test
    void myBookings_listsOnlyMine_newestFirst_withSeatsAndStatus() throws Exception {
        long first = paid(alice, tomorrow, "A1", "A2");
        long second = held(alice, nextWeek, "C1");
        held(bob, tomorrow, "B1");

        mockMvc.perform(get("/api/bookings/me").with(bearer(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[*].id", contains((int) second, (int) first)))
                .andExpect(jsonPath("$.content[1].status").value("CONFIRMED"))
                .andExpect(jsonPath("$.content[1].seats", contains("A1", "A2")))
                .andExpect(jsonPath("$.content[1].totalAmount").value(400.00))
                .andExpect(jsonPath("$.content[1].movieTitle").isString())
                .andExpect(jsonPath("$.content[1].cityName").isString())
                .andExpect(jsonPath("$.content[0].status").value("HELD"))
                .andExpect(jsonPath("$.content[0].seats", contains("C1")));
    }

    @Test
    void statusFilter_usesTheEffectiveStatus() throws Exception {
        long confirmed = paid(alice, tomorrow, "A1");
        long expiring = held(alice, nextWeek, "A1");

        mockMvc.perform(get("/api/bookings/me").param("status", "HELD").with(bearer(alice)))
                .andExpect(jsonPath("$.content[*].id", contains((int) expiring)));

        clock.advance(Duration.ofMinutes(11)); // hold ran out, sweeper has not run

        mockMvc.perform(get("/api/bookings/me").param("status", "HELD").with(bearer(alice)))
                .andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get("/api/bookings/me").param("status", "EXPIRED").with(bearer(alice)))
                .andExpect(jsonPath("$.content[*].id", contains((int) expiring)))
                .andExpect(jsonPath("$.content[0].status").value("EXPIRED"));
        mockMvc.perform(get("/api/bookings/me").param("status", "CONFIRMED").with(bearer(alice)))
                .andExpect(jsonPath("$.content[*].id", contains((int) confirmed)));
    }

    @Test
    void cancelledBookingsShowTheirRefund() throws Exception {
        long bookingId = paid(alice, nextWeek, "A1");
        mockMvc.perform(post("/api/bookings/{id}/cancel", bookingId).with(bearer(alice))).andExpect(status().isOk());

        mockMvc.perform(get("/api/bookings/me").param("status", "CANCELLED").with(bearer(alice)))
                .andExpect(jsonPath("$.content[0].id").value(bookingId))
                .andExpect(jsonPath("$.content[0].refundAmount").value(0)); // no refund policy configured
    }

    @Test
    void upcomingAndPast_splitByShowStart_andSortByShowTime() throws Exception {
        long soon = paid(alice, tomorrow, "A1");
        long later = paid(alice, nextWeek, "A1");

        mockMvc.perform(get("/api/bookings/me").param("sort", "show.startTime,asc").with(bearer(alice)))
                .andExpect(jsonPath("$.content[*].id", contains((int) soon, (int) later)));

        clock.advance(Duration.ofDays(2)); // tomorrow's show is now in the past
        mockMvc.perform(get("/api/bookings/me").param("when", "UPCOMING").with(bearer(alice)))
                .andExpect(jsonPath("$.content[*].id", contains((int) later)));
        mockMvc.perform(get("/api/bookings/me").param("when", "PAST").with(bearer(alice)))
                .andExpect(jsonPath("$.content[*].id", contains((int) soon)));
    }

    @Test
    void pagination_andInvalidParameters() throws Exception {
        for (String seat : new String[]{"A1", "A2", "A3"}) {
            long id = held(alice, tomorrow, seat);
            mockMvc.perform(delete("/api/bookings/{id}/hold", id).with(bearer(alice))).andExpect(status().isOk());
        }

        mockMvc.perform(get("/api/bookings/me").param("size", "2").param("page", "1").with(bearer(alice)))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content[0].status").value("RELEASED"));
        mockMvc.perform(get("/api/bookings/me").param("sort", "user.passwordHash").with(bearer(alice)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_PARAMETER"));
        mockMvc.perform(get("/api/bookings/me").param("status", "LOST").with(bearer(alice)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/bookings/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void adminSearch_filtersByShowCustomerAndStatus() throws Exception {
        long alicePaid = paid(alice, tomorrow, "A1");
        long bobHeld = held(bob, tomorrow, "A2");
        paid(alice, nextWeek, "A1");

        mockMvc.perform(get("/api/admin/bookings").param("showId", String.valueOf(tomorrow.showId())).with(bearer(admin)))
                .andExpect(jsonPath("$.content[*].id", containsInAnyOrder((int) alicePaid, (int) bobHeld)));
        mockMvc.perform(get("/api/admin/bookings").param("email", aliceUser.getEmail().toUpperCase())
                        .param("status", "CONFIRMED").with(bearer(admin)))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].customerEmail").value(aliceUser.getEmail()));
        mockMvc.perform(get("/api/admin/bookings").param("userId", String.valueOf(aliceUser.getId()))
                        .param("showId", String.valueOf(tomorrow.showId())).with(bearer(admin)))
                .andExpect(jsonPath("$.content[*].id", contains((int) alicePaid)));

        mockMvc.perform(get("/api/admin/bookings").with(bearer(alice))).andExpect(status().isForbidden());
    }

    @Test
    void showSales_reportOccupancyAndMoney() throws Exception {
        paid(alice, tomorrow, "A1", "C1");      // 200 + 350 = 550
        long refunded = paid(bob, tomorrow, "A2"); // 200, cancelled below
        String carol = login(mockMvc, data.user(Role.CUSTOMER).getEmail(), TestData.PASSWORD);
        held(carol, tomorrow, "B1", "B2");
        mockMvc.perform(post("/api/admin/refund-policies").with(bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"All\", \"active\": true, \"rules\": [{\"minHoursBeforeShow\": 0, \"refundPercent\": 100}]}"))
                .andExpect(status().isCreated());
        // Bob paid before the policy existed -> no policy on his booking -> refund 0
        mockMvc.perform(post("/api/bookings/{id}/cancel", refunded).with(bearer(bob))).andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/shows/{id}/sales", tomorrow.showId()).with(bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSeats").value(15))
                .andExpect(jsonPath("$.bookedSeats").value(2))
                .andExpect(jsonPath("$.heldSeats").value(2))
                .andExpect(jsonPath("$.availableSeats").value(11))
                .andExpect(jsonPath("$.occupancyPercent").value(13.3))
                .andExpect(jsonPath("$.confirmedBookings").value(1))
                .andExpect(jsonPath("$.cancelledBookings").value(1))
                .andExpect(jsonPath("$.grossPaid").value(750.00))
                .andExpect(jsonPath("$.refunded").value(0))
                .andExpect(jsonPath("$.netRevenue").value(750.00));

        clock.advance(Duration.ofMinutes(11)); // Carol's hold lapses
        mockMvc.perform(get("/api/admin/shows/{id}/sales", tomorrow.showId()).with(bearer(admin)))
                .andExpect(jsonPath("$.heldSeats").value(0))
                .andExpect(jsonPath("$.availableSeats").value(13));
    }

    // ---------- helpers ----------

    private long held(String token, ShowFixture show, String... seats) throws Exception {
        String ids = Arrays.stream(seats).map(l -> String.valueOf(show.seat(l))).collect(Collectors.joining(", "));
        return idOf(mockMvc.perform(post("/api/shows/{id}/holds", show.showId()).with(bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content("{\"showSeatIds\": [" + ids + "]}")));
    }

    private long paid(String token, ShowFixture show, String... seats) throws Exception {
        long id = held(token, show, seats);
        mockMvc.perform(post("/api/bookings/{id}/pay", id).with(bearer(token))
                        .header("Idempotency-Key", "k-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"method\": \"CARD\"}"))
                .andExpect(status().isOk());
        return id;
    }

    private static long idOf(ResultActions result) throws Exception {
        String json = result.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }
}
