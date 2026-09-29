package com.sufiyan.moviebooking.journey;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.sufiyan.moviebooking.support.MutableClock;
import com.sufiyan.moviebooking.support.MutableClockConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

import static com.sufiyan.moviebooking.support.AuthTestSupport.bearer;
import static com.sufiyan.moviebooking.support.AuthTestSupport.login;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The whole product through its public API only, as a client would use it: an admin sets up a cinema, a customer
 * finds a Saturday prime-time show, holds premium seats, applies a code, pays, and later cancels with a refund.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(MutableClockConfig.class)
@Transactional
class CustomerJourneyIT {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    @Autowired private MockMvc mockMvc;
    @Autowired private MutableClock clock;

    @Test
    void adminSetsUpACinema_customerBooksPaysAndCancelsWithARefund() throws Exception {
        clock.reset();
        // A Saturday at least a week away, 19:00: weekend + prime time apply
        OffsetDateTime showTime = LocalDate.now(IST).plusDays(7).with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY))
                .atTime(19, 0).atZone(IST).toOffsetDateTime();

        // ---- 1. The bootstrap admin sets everything up ----
        String admin = login(mockMvc, "admin@test.local", "admin-test-password");
        long city = id(as(admin, post("/api/admin/cities"), "{\"name\": \"Journey City\", \"state\": \"MH\"}"));
        long theater = id(as(admin, post("/api/admin/theaters"),
                "{\"cityId\": " + city + ", \"name\": \"Grand Cinema\", \"address\": \"Main Road\"}"));
        long screen = id(as(admin, post("/api/admin/theaters/" + theater + "/screens"), "{\"name\": \"Audi 1\"}"));
        as(admin, put("/api/admin/screens/" + screen + "/layout"), """
                {"sections": [{"rows": "A-H", "seatsPerRow": 12, "seatType": "REGULAR"},
                              {"rows": "I-J", "seatsPerRow": 10, "seatType": "PREMIUM"}]}
                """).andExpect(status().isOk()).andExpect(jsonPath("$.totalSeats").value(116));
        long movie = id(as(admin, post("/api/admin/movies"), """
                {"title": "Journey Movie", "language": "English", "genre": "Drama", "durationMinutes": 150}
                """));
        as(admin, post("/api/admin/pricing-rules"),
                "{\"name\": \"Weekend\", \"ruleType\": \"WEEKEND\", \"adjustmentPercent\": 20}").andExpect(status().isCreated());
        as(admin, post("/api/admin/pricing-rules"), """
                {"name": "Prime time", "ruleType": "PRIME_TIME", "adjustmentPercent": 10,
                 "windowStart": "18:00", "windowEnd": "22:00"}
                """).andExpect(status().isCreated());
        as(admin, post("/api/admin/discount-codes"), """
                {"code": "WELCOME10", "discountType": "PERCENT", "discountValue": 10, "maxDiscountAmount": 100,
                 "perUserLimit": 1}
                """).andExpect(status().isCreated());
        as(admin, post("/api/admin/refund-policies"), """
                {"name": "Standard", "active": true, "rules": [{"minHoursBeforeShow": 48, "refundPercent": 100},
                                                               {"minHoursBeforeShow": 24, "refundPercent": 50}]}
                """).andExpect(status().isCreated());
        long show = id(as(admin, post("/api/admin/shows"), """
                {"movieId": %d, "screenId": %d, "startTime": "%s", "prices": {"REGULAR": 200, "PREMIUM": 350}}
                """.formatted(movie, screen, showTime)));

        // ---- 2. A customer signs up and browses without logging in ----
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Asha\", \"email\": \"asha@example.com\", \"password\": \"journey-pass-1\"}"))
                .andExpect(status().isCreated());
        String asha = login(mockMvc, "asha@example.com", "journey-pass-1");

        mockMvc.perform(get("/api/shows").param("cityId", String.valueOf(city))
                        .param("date", showTime.toLocalDate().toString()))
                .andExpect(jsonPath("$.content[0].id").value(show))
                .andExpect(jsonPath("$.content[0].effectivePrices.PREMIUM").value(455.00)) // 350 * 1.30
                .andExpect(jsonPath("$.content[0].appliedPricingRules", contains("Prime time", "Weekend")));
        DocumentContext seatMap = json(mockMvc.perform(get("/api/shows/{id}/seats", show)));
        long j1 = seatId(seatMap, "J1");
        long j2 = seatId(seatMap, "J2");

        // ---- 3. Hold two premium seats, apply a code, pay ----
        long booking = id(as(asha, post("/api/shows/" + show + "/holds"), "{\"showSeatIds\": [" + j1 + ", " + j2 + "]}"));
        as(asha, post("/api/bookings/" + booking + "/discount"), "{\"code\": \"welcome10\"}")
                .andExpect(jsonPath("$.subtotalAmount").value(910.00))
                .andExpect(jsonPath("$.discountAmount").value(91.00))
                .andExpect(jsonPath("$.totalAmount").value(819.00));

        String key = "journey-" + booking;
        mockMvc.perform(post("/api/bookings/{id}/pay", booking).with(bearer(asha)).header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"method\": \"UPI\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(819.00))
                .andExpect(jsonPath("$.booking.status").value("CONFIRMED"));
        // A network retry of the same request does not charge again
        mockMvc.perform(post("/api/bookings/{id}/pay", booking).with(bearer(asha)).header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"method\": \"UPI\"}"))
                .andExpect(status().isOk()).andExpect(header().string("Idempotent-Replayed", "true"));

        mockMvc.perform(get("/api/bookings/me").with(bearer(asha)))
                .andExpect(jsonPath("$.content[0].status").value("CONFIRMED"))
                .andExpect(jsonPath("$.content[0].seats", contains("J1", "J2")));
        mockMvc.perform(get("/api/admin/shows/{id}/sales", show).with(bearer(admin)))
                .andExpect(jsonPath("$.bookedSeats").value(2))
                .andExpect(jsonPath("$.grossPaid").value(819.00));

        // ---- 4. 30 hours before the show she cancels: 50% back under the Standard policy ----
        clock.advance(Duration.between(clock.instant(), showTime.toInstant().minus(Duration.ofHours(30))));
        mockMvc.perform(get("/api/bookings/{id}/refund-quote", booking).with(bearer(asha)))
                .andExpect(jsonPath("$.refundPercent").value(50))
                .andExpect(jsonPath("$.refundAmount").value(409.50));
        mockMvc.perform(post("/api/bookings/{id}/cancel", booking).with(bearer(asha)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.refund.amount").value(409.50));

        // Seats are back on sale, and the books balance
        mockMvc.perform(get("/api/shows/{id}/seats", show))
                .andExpect(jsonPath("$.summary.BOOKED").value(0))
                .andExpect(jsonPath("$.summary.AVAILABLE").value(116));
        mockMvc.perform(get("/api/admin/shows/{id}/sales", show).with(bearer(admin)))
                .andExpect(jsonPath("$.grossPaid").value(819.00))
                .andExpect(jsonPath("$.refunded").value(409.50))
                .andExpect(jsonPath("$.netRevenue").value(409.50));
    }

    private ResultActions as(String token, MockHttpServletRequestBuilder request, String body) throws Exception {
        return mockMvc.perform(request.with(bearer(token)).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static long seatId(DocumentContext seatMap, String label) {
        return ((Number) seatMap.read("$.rows[*].seats[?(@.label == '" + label + "')].showSeatId", List.class)
                .getFirst()).longValue();
    }

    private static DocumentContext json(ResultActions result) throws Exception {
        return JsonPath.parse(result.andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private static long id(ResultActions result) throws Exception {
        String json = result.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }
}
