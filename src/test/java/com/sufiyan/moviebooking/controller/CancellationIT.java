package com.sufiyan.moviebooking.controller;

import com.jayway.jsonpath.JsonPath;
import com.sufiyan.moviebooking.entity.BookingStatus;
import com.sufiyan.moviebooking.entity.DiscountCode;
import com.sufiyan.moviebooking.entity.DiscountType;
import com.sufiyan.moviebooking.entity.Role;
import com.sufiyan.moviebooking.entity.ShowSeatStatus;
import com.sufiyan.moviebooking.repository.BookingRepository;
import com.sufiyan.moviebooking.repository.DiscountCodeRepository;
import com.sufiyan.moviebooking.repository.ShowSeatRepository;
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

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import static com.sufiyan.moviebooking.support.AuthTestSupport.bearer;
import static com.sufiyan.moviebooking.support.AuthTestSupport.login;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(MutableClockConfig.class)
@Transactional
class CancellationIT {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final String STANDARD_POLICY = """
            {"name": "Standard", "active": true,
             "rules": [{"minHoursBeforeShow": 48, "refundPercent": 100},
                       {"minHoursBeforeShow": 24, "refundPercent": 50}]}
            """;

    @Autowired private MockMvc mockMvc;
    @Autowired private ApplicationContext ctx;
    @Autowired private MutableClock clock;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private ShowSeatRepository showSeatRepository;
    @Autowired private DiscountCodeRepository discountCodeRepository;

    private TestData data;
    private ShowFixture show;
    private Instant showStart;
    private String alice;
    private String bob;
    private String admin;

    @BeforeEach
    void setUp() throws Exception {
        clock.reset();
        data = new TestData(ctx);
        OffsetDateTime start = LocalDate.now(IST).plusDays(5).atTime(18, 0).atZone(IST).toOffsetDateTime();
        showStart = start.toInstant();
        show = data.show(start); // A/B regular 200, C premium 350; no pricing rules
        alice = login(mockMvc, data.user(Role.CUSTOMER).getEmail(), TestData.PASSWORD);
        bob = login(mockMvc, data.user(Role.CUSTOMER).getEmail(), TestData.PASSWORD);
        admin = login(mockMvc, data.user(Role.ADMIN).getEmail(), TestData.PASSWORD);
    }

    // ---------- customer cancellation ----------

    @Test
    void cancellingWellAhead_refundsInFull_andFreesTheSeats() throws Exception {
        adminPost("/api/admin/refund-policies", STANDARD_POLICY).andExpect(status().isCreated());
        long bookingId = paidBooking(alice, "A1", "A2"); // 400
        moveTo(showStart.minus(Duration.ofHours(72)));

        mockMvc.perform(get("/api/bookings/{id}/refund-quote", bookingId).with(bearer(alice)))
                .andExpect(jsonPath("$.cancellable").value(true))
                .andExpect(jsonPath("$.policyName").value("Standard"))
                .andExpect(jsonPath("$.hoursBeforeShow").value(72))
                .andExpect(jsonPath("$.refundPercent").value(100))
                .andExpect(jsonPath("$.refundAmount").value(400.00));

        cancel(alice, bookingId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancelledAt").isString())
                .andExpect(jsonPath("$.refund.amount").value(400.00))
                .andExpect(jsonPath("$.refund.refundPercent").value(100))
                .andExpect(jsonPath("$.refund.reason").value("CUSTOMER_CANCELLATION"))
                .andExpect(jsonPath("$.refund.policyName").value("Standard"));

        assertThat(showSeatRepository.findById(show.seat("A1")).orElseThrow().getStatus()).isEqualTo(ShowSeatStatus.AVAILABLE);
        mockMvc.perform(get("/api/shows/{id}/seats", show.showId())).andExpect(jsonPath("$.summary.BOOKED").value(0));
        hold(bob, show.seat("A1")).andExpect(status().isCreated()); // back on sale
    }

    @Test
    void refundFollowsThePolicyBands() throws Exception {
        adminPost("/api/admin/refund-policies", STANDARD_POLICY).andExpect(status().isCreated());
        long halfRefund = paidBooking(alice, "A1");                            // 200
        long noRefund = paidBooking(bob, "A2");                                // 200

        moveTo(showStart.minus(Duration.ofHours(30)));
        cancel(alice, halfRefund).andExpect(jsonPath("$.refund.amount").value(100.00))
                .andExpect(jsonPath("$.refund.refundPercent").value(50));

        moveTo(showStart.minus(Duration.ofHours(5)));
        cancel(bob, noRefund)
                .andExpect(status().isOk()) // still cancellable, just no money back
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.refund.amount").value(0))
                .andExpect(jsonPath("$.refund.refundPercent").value(0));
    }

    @Test
    void refundIsBasedOnWhatWasActuallyPaid() throws Exception {
        adminPost("/api/admin/refund-policies", STANDARD_POLICY).andExpect(status().isCreated());
        discountCodeRepository.save(new DiscountCode("TENOFF", DiscountType.PERCENT, new BigDecimal("10")));
        long bookingId = idOf(hold(alice, show.seat("A1"), show.seat("A2")));   // 400
        mockMvc.perform(post("/api/bookings/{id}/discount", bookingId).with(bearer(alice))
                .contentType(MediaType.APPLICATION_JSON).content("{\"code\": \"TENOFF\"}")).andExpect(status().isOk());
        pay(alice, bookingId).andExpect(jsonPath("$.amount").value(360.00));
        moveTo(showStart.minus(Duration.ofHours(30)));

        cancel(alice, bookingId).andExpect(jsonPath("$.refund.amount").value(180.00));
    }

    @Test
    void policyInForceAtPayment_isTheOneUsed_evenIfItChangesLater() throws Exception {
        adminPost("/api/admin/refund-policies", STANDARD_POLICY).andExpect(status().isCreated());
        long bookingId = paidBooking(alice, "A1");
        adminPost("/api/admin/refund-policies", """
                {"name": "Strict", "active": true, "rules": [{"minHoursBeforeShow": 0, "refundPercent": 0}]}
                """).andExpect(status().isCreated());
        moveTo(showStart.minus(Duration.ofHours(72)));

        cancel(alice, bookingId).andExpect(jsonPath("$.refund.amount").value(200.00))
                .andExpect(jsonPath("$.refund.policyName").value("Standard"));
        // New bookings get the new policy
        long later = paidBooking(bob, "A2");
        cancel(bob, later).andExpect(jsonPath("$.refund.amount").value(0))
                .andExpect(jsonPath("$.refund.policyName").value("Strict"));
    }

    @Test
    void withoutAnyPolicy_cancellationRefundsNothing() throws Exception {
        long bookingId = paidBooking(alice, "A1");

        cancel(alice, bookingId).andExpect(status().isOk())
                .andExpect(jsonPath("$.refund.amount").value(0))
                .andExpect(jsonPath("$.refund.policyName").doesNotExist());
    }

    @Test
    void cancellationIsRejectedWhenNotAllowed() throws Exception {
        long held = idOf(hold(alice, show.seat("A1")));
        cancel(alice, held).andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("BOOKING_NOT_CONFIRMED"));
        mockMvc.perform(delete("/api/bookings/{id}/hold", held).with(bearer(alice))).andExpect(status().isOk());

        long paid = paidBooking(alice, "A2");
        cancel(bob, paid).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/bookings/{id}/refund-quote", paid).with(bearer(bob))).andExpect(status().isNotFound());

        moveTo(showStart.plusSeconds(60));
        mockMvc.perform(get("/api/bookings/{id}/refund-quote", paid).with(bearer(alice)))
                .andExpect(jsonPath("$.cancellable").value(false))
                .andExpect(jsonPath("$.reason").value("The show has already started"));
        cancel(alice, paid).andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("CANCELLATION_CLOSED"));
    }

    @Test
    void cancellingTwice_isRejected() throws Exception {
        long bookingId = paidBooking(alice, "A1");
        cancel(alice, bookingId).andExpect(status().isOk());

        cancel(alice, bookingId).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("BOOKING_ALREADY_CANCELLED"));
    }

    // ---------- admin: refund policies ----------

    @Test
    void refundPolicyAdmin_validatesBands_keepsOneActive_andFreezesUsedPolicies() throws Exception {
        adminPost("/api/admin/refund-policies", """
                {"name": "Backwards", "rules": [{"minHoursBeforeShow": 48, "refundPercent": 50},
                                                {"minHoursBeforeShow": 24, "refundPercent": 100}]}
                """).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("INVALID_REFUND_POLICY"));
        adminPost("/api/admin/refund-policies", """
                {"name": "Dupes", "rules": [{"minHoursBeforeShow": 24, "refundPercent": 50},
                                            {"minHoursBeforeShow": 24, "refundPercent": 40}]}
                """).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("INVALID_REFUND_POLICY"));
        adminPost("/api/admin/refund-policies", """
                {"name": "TooMuch", "rules": [{"minHoursBeforeShow": 24, "refundPercent": 150}]}
                """).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("VALIDATION_FAILED"));
        adminPost("/api/admin/refund-policies", """
                {"name": "Empty", "rules": []}
                """).andExpect(status().isBadRequest());

        long standard = idOf(adminPost("/api/admin/refund-policies", STANDARD_POLICY));
        long lenient = idOf(adminPost("/api/admin/refund-policies", """
                {"name": "Lenient", "rules": [{"minHoursBeforeShow": 2, "refundPercent": 100}]}
                """));
        mockMvc.perform(post("/api/admin/refund-policies/{id}/activate", lenient).with(bearer(admin)))
                .andExpect(jsonPath("$.active").value(true));
        mockMvc.perform(get("/api/admin/refund-policies/{id}", standard).with(bearer(admin)))
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.rules[0].minHoursBeforeShow").value(48));

        // Unused policies can be edited; a policy on a paid booking is frozen
        mockMvc.perform(put("/api/admin/refund-policies/{id}", lenient).with(bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Lenient\", \"rules\": [{\"minHoursBeforeShow\": 1, \"refundPercent\": 90}]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.rules[0].refundPercent").value(90));
        paidBooking(alice, "A1");
        mockMvc.perform(put("/api/admin/refund-policies/{id}", lenient).with(bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Lenient\", \"rules\": [{\"minHoursBeforeShow\": 1, \"refundPercent\": 10}]}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("REFUND_POLICY_IN_USE"));
        mockMvc.perform(get("/api/admin/refund-policies/{id}", lenient).with(bearer(admin)))
                .andExpect(jsonPath("$.inUse").value(true));
        mockMvc.perform(delete("/api/admin/refund-policies/{id}", lenient).with(bearer(admin)))
                .andExpect(status().isConflict());
        mockMvc.perform(delete("/api/admin/refund-policies/{id}", standard).with(bearer(admin)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/admin/refund-policies").with(bearer(alice))).andExpect(status().isForbidden());
    }

    // ---------- admin: show cancellation ----------

    @Test
    void cancellingAShow_refundsPaidBookingsInFull_releasesHolds_andClosesTheShow() throws Exception {
        adminPost("/api/admin/refund-policies", """
                {"name": "NoRefunds", "active": true, "rules": [{"minHoursBeforeShow": 0, "refundPercent": 0}]}
                """).andExpect(status().isCreated());
        long alicePaid = paidBooking(alice, "A1", "C1");  // 550
        long bobPaid = paidBooking(bob, "A2");            // 200
        String carol = login(mockMvc, data.user(Role.CUSTOMER).getEmail(), TestData.PASSWORD);
        long carolHeld = idOf(hold(carol, show.seat("B1")));

        cancelShow()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refundedBookings").value(2))
                .andExpect(jsonPath("$.releasedHolds").value(1))
                .andExpect(jsonPath("$.totalRefunded").value(750.00))
                .andExpect(jsonPath("$.failedBookingIds").isEmpty());

        // Full refund even though the policy says 0%
        mockMvc.perform(get("/api/bookings/{id}", alicePaid).with(bearer(alice)))
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.refund.amount").value(550.00))
                .andExpect(jsonPath("$.refund.reason").value("SHOW_CANCELLED"));
        assertThat(bookingRepository.findById(bobPaid).orElseThrow().getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(bookingRepository.findById(carolHeld).orElseThrow().getStatus()).isEqualTo(BookingStatus.RELEASED);

        // Show is closed: not listed, cannot be held, the released hold cannot be paid
        mockMvc.perform(get("/api/shows/{id}", show.showId())).andExpect(jsonPath("$.status").value("CANCELLED"));
        mockMvc.perform(get("/api/shows").param("movieId", "0")).andExpect(status().isOk());
        hold(bob, show.seat("A3")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("SHOW_NOT_BOOKABLE"));
        pay(carol, carolHeld).andExpect(status().isConflict());

        // Re-running only resumes: nothing left to do
        cancelShow().andExpect(jsonPath("$.refundedBookings").value(0)).andExpect(jsonPath("$.releasedHolds").value(0));
    }

    @Test
    void heldBookingOfACancelledShow_cannotBePaid() throws Exception {
        long held = idOf(hold(alice, show.seat("A1")));
        // Simulate a hold created in the window just before the cancellation committed
        cancelShow().andExpect(status().isOk());
        bookingRepository.findById(held).orElseThrow().setStatus(BookingStatus.HELD);

        pay(alice, held).andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("SHOW_CANCELLED"));
    }

    @Test
    void showThatHasEnded_cannotBeCancelled_andCustomersCannotCancelShows() throws Exception {
        mockMvc.perform(post("/api/admin/shows/{id}/cancel", show.showId()).with(bearer(alice))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\": \"x\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/shows/{id}/cancel", show.showId()).with(bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\": \"\"}"))
                .andExpect(status().isBadRequest());

        moveTo(showStart.plus(Duration.ofHours(5)));
        cancelShow().andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("SHOW_ALREADY_ENDED"));
    }

    // ---------- helpers ----------

    private void moveTo(Instant target) {
        clock.advance(Duration.between(clock.instant(), target));
    }

    private long paidBooking(String token, String... seats) throws Exception {
        long bookingId = idOf(hold(token, Arrays.stream(seats).mapToLong(show::seat).toArray()));
        pay(token, bookingId).andExpect(status().isOk());
        return bookingId;
    }

    private ResultActions pay(String token, long bookingId) throws Exception {
        return mockMvc.perform(post("/api/bookings/{id}/pay", bookingId).with(bearer(token))
                .header("Idempotency-Key", "k-" + UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON).content("{\"method\": \"CARD\"}"));
    }

    private ResultActions cancel(String token, long bookingId) throws Exception {
        return mockMvc.perform(post("/api/bookings/{id}/cancel", bookingId).with(bearer(token)));
    }

    private ResultActions cancelShow() throws Exception {
        return mockMvc.perform(post("/api/admin/shows/{id}/cancel", show.showId()).with(bearer(admin))
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\": \"Projector failure\"}"));
    }

    private ResultActions hold(String token, long... seats) throws Exception {
        String ids = LongStream.of(seats).mapToObj(String::valueOf).collect(Collectors.joining(", "));
        return mockMvc.perform(post("/api/shows/{id}/holds", show.showId()).with(bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content("{\"showSeatIds\": [" + ids + "]}"));
    }

    private ResultActions adminPost(String path, String json) throws Exception {
        return mockMvc.perform(post(path).with(bearer(admin)).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private static long idOf(ResultActions result) throws Exception {
        String json = result.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }
}
