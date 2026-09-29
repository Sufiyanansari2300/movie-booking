package com.sufiyan.moviebooking.controller;

import com.jayway.jsonpath.JsonPath;
import com.sufiyan.moviebooking.entity.BookingStatus;
import com.sufiyan.moviebooking.entity.DiscountCode;
import com.sufiyan.moviebooking.entity.DiscountType;
import com.sufiyan.moviebooking.entity.Role;
import com.sufiyan.moviebooking.entity.ShowSeatStatus;
import com.sufiyan.moviebooking.repository.BookingRepository;
import com.sufiyan.moviebooking.repository.DiscountCodeRepository;
import com.sufiyan.moviebooking.repository.DiscountRedemptionRepository;
import com.sufiyan.moviebooking.repository.PaymentRepository;
import com.sufiyan.moviebooking.repository.ShowSeatRepository;
import com.sufiyan.moviebooking.service.HoldExpiryJob;
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
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import static com.sufiyan.moviebooking.support.AuthTestSupport.bearer;
import static com.sufiyan.moviebooking.support.AuthTestSupport.login;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(MutableClockConfig.class)
@Transactional
class PaymentIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private ApplicationContext ctx;
    @Autowired private MutableClock clock;
    @Autowired private HoldExpiryJob expiryJob;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private ShowSeatRepository showSeatRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private DiscountCodeRepository discountCodeRepository;
    @Autowired private DiscountRedemptionRepository redemptionRepository;

    private ShowFixture show;
    private String alice;
    private String bob;

    @BeforeEach
    void setUp() throws Exception {
        clock.reset();
        TestData data = new TestData(ctx);
        show = data.show(); // tomorrow 18:00, no pricing rules: A/B = 200, C = 350
        alice = login(mockMvc, data.user(Role.CUSTOMER).getEmail(), TestData.PASSWORD);
        bob = login(mockMvc, data.user(Role.CUSTOMER).getEmail(), TestData.PASSWORD);
    }

    @Test
    void successfulPayment_confirmsBookingAndBooksSeats() throws Exception {
        long bookingId = idOf(hold(alice, show.seat("A1"), show.seat("C1")));

        pay(alice, bookingId, newKey(), "tok_success")
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotent-Replayed", "false"))
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.amount").value(550.00))
                .andExpect(jsonPath("$.currency").value("INR"))
                .andExpect(jsonPath("$.method").value("CARD"))
                .andExpect(jsonPath("$.gatewayReference", startsWith("mock_")))
                .andExpect(jsonPath("$.booking.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.booking.confirmedAt").isString());

        mockMvc.perform(get("/api/shows/{id}/seats", show.showId()))
                .andExpect(jsonPath("$.summary.BOOKED").value(2))
                .andExpect(jsonPath("$.summary.HELD").value(0));
        // Booked seats cannot be held by anyone
        hold(bob, show.seat("A1")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("SEATS_UNAVAILABLE"));
        // A confirmed booking cannot be released
        mockMvc.perform(delete("/api/bookings/{id}/hold", bookingId).with(bearer(alice)))
                .andExpect(status().isConflict());
    }

    @Test
    void confirmedBooking_isNotTouchedByTheExpirySweeper() throws Exception {
        long bookingId = idOf(hold(alice, show.seat("A1")));
        pay(alice, bookingId, newKey(), "tok_success").andExpect(status().isOk());

        clock.advance(Duration.ofMinutes(30));
        assertThat(expiryJob.sweep()).isZero();

        assertThat(bookingRepository.findById(bookingId).orElseThrow().getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(showSeatRepository.findById(show.seat("A1")).orElseThrow().getStatus()).isEqualTo(ShowSeatStatus.BOOKED);
        mockMvc.perform(get("/api/bookings/{id}", bookingId).with(bearer(alice)))
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void retryWithSameIdempotencyKey_returnsTheSameResult_withoutChargingAgain() throws Exception {
        long bookingId = idOf(hold(alice, show.seat("A1")));
        String key = newKey();

        String first = pay(alice, bookingId, key, "tok_success").andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        pay(alice, bookingId, key, "tok_success")
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotent-Replayed", "true"))
                .andExpect(jsonPath("$.paymentId").value(((Number) JsonPath.read(first, "$.paymentId")).intValue()))
                .andExpect(jsonPath("$.booking.status").value("CONFIRMED"));

        assertThat(paymentRepository.countByBookingId(bookingId)).isEqualTo(1);
    }

    @Test
    void differentKeyForAnAlreadyPaidBooking_isRejected() throws Exception {
        long bookingId = idOf(hold(alice, show.seat("A1")));
        pay(alice, bookingId, newKey(), "tok_success").andExpect(status().isOk());

        pay(alice, bookingId, newKey(), "tok_success")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("BOOKING_ALREADY_CONFIRMED"));
        assertThat(paymentRepository.countByBookingId(bookingId)).isEqualTo(1);
    }

    @Test
    void idempotencyKeyReusedForAnotherBooking_isRejected() throws Exception {
        String key = newKey();
        pay(alice, idOf(hold(alice, show.seat("A1"))), key, "tok_success").andExpect(status().isOk());
        long bobsBooking = idOf(hold(bob, show.seat("A2")));

        pay(bob, bobsBooking, key, "tok_success")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("IDEMPOTENCY_KEY_REUSED"));
    }

    @Test
    void missingOrMalformedIdempotencyKey_returns400() throws Exception {
        long bookingId = idOf(hold(alice, show.seat("A1")));

        mockMvc.perform(post("/api/bookings/{id}/pay", bookingId).with(bearer(alice))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"method\": \"CARD\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_PARAMETER"));
        pay(alice, bookingId, "short", "tok_success")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_IDEMPOTENCY_KEY"));
        mockMvc.perform(post("/api/bookings/{id}/pay", bookingId).with(bearer(alice))
                        .header("Idempotency-Key", newKey())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"method\": \"CASH\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void declinedPayment_isRecorded_bookingStaysHeld_andCanBeRetried() throws Exception {
        long bookingId = idOf(hold(alice, show.seat("A1")));

        pay(alice, bookingId, newKey(), "tok_declined")
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.failureCode").value("CARD_DECLINED"))
                .andExpect(jsonPath("$.booking.status").value("HELD"));
        pay(alice, bookingId, newKey(), "tok_error")
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.failureCode").value("GATEWAY_ERROR"));
        assertThat(showSeatRepository.findById(show.seat("A1")).orElseThrow().getStatus()).isEqualTo(ShowSeatStatus.HELD);

        pay(alice, bookingId, newKey(), "tok_success")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.booking.status").value("CONFIRMED"));

        mockMvc.perform(get("/api/bookings/{id}/payments", bookingId).with(bearer(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].status").value("FAILED"))
                .andExpect(jsonPath("$[2].status").value("SUCCEEDED"));
    }

    @Test
    void expiredHold_cannotBePaid() throws Exception {
        long bookingId = idOf(hold(alice, show.seat("A1")));
        clock.advance(Duration.ofMinutes(11));

        pay(alice, bookingId, newKey(), "tok_success")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("HOLD_EXPIRED"));
        assertThat(paymentRepository.countByBookingId(bookingId)).as("gateway never called").isZero();
    }

    @Test
    void releasedBooking_cannotBePaid_andOthersCannotPayYourBooking() throws Exception {
        long bookingId = idOf(hold(alice, show.seat("A1")));

        pay(bob, bookingId, newKey(), "tok_success").andExpect(status().isNotFound());
        mockMvc.perform(get("/api/bookings/{id}/payments", bookingId).with(bearer(bob)))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/bookings/{id}/hold", bookingId).with(bearer(alice))).andExpect(status().isOk());
        pay(alice, bookingId, newKey(), "tok_success")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("BOOKING_NOT_HELD"));
    }

    @Test
    void discountIsChargedAndItsUseCounted_onlyWhenPaid() throws Exception {
        DiscountCode code = new DiscountCode("ONCE10", DiscountType.PERCENT, new BigDecimal("10"));
        code.setPerUserLimit(1);
        discountCodeRepository.save(code);
        long bookingId = idOf(hold(alice, show.seat("A1"), show.seat("A2"))); // 400
        applyCode(alice, bookingId, "ONCE10").andExpect(status().isOk());
        assertThat(discountCodeRepository.findByCode("ONCE10").orElseThrow().getUsedCount()).isZero();

        pay(alice, bookingId, newKey(), "tok_success")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(360.00))
                .andExpect(jsonPath("$.booking.discountCode").value("ONCE10"));

        assertThat(discountCodeRepository.findByCode("ONCE10").orElseThrow().getUsedCount()).isEqualTo(1);
        assertThat(redemptionRepository.countByDiscountCodeIdAndUserId(code.getId(),
                bookingRepository.findById(bookingId).orElseThrow().getUser().getId())).isEqualTo(1);

        // Per-customer limit now applies to Alice's next booking
        ShowFixture other = new TestData(ctx).show();
        long next = idOf(mockMvc.perform(post("/api/shows/{id}/holds", other.showId()).with(bearer(alice))
                .contentType(MediaType.APPLICATION_JSON).content("{\"showSeatIds\": [" + other.seat("A1") + "]}")));
        applyCode(alice, next, "ONCE10").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("DISCOUNT_USER_LIMIT_REACHED"));
    }

    @Test
    void codeThatBecameInvalidAfterApplying_blocksPayment_untilRemoved() throws Exception {
        DiscountCode code = discountCodeRepository.save(new DiscountCode("FLASH", DiscountType.FLAT, new BigDecimal("50")));
        long bookingId = idOf(hold(alice, show.seat("A1")));
        applyCode(alice, bookingId, "FLASH").andExpect(status().isOk());
        code.setActive(false); // admin switches it off before Alice pays

        pay(alice, bookingId, newKey(), "tok_success")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("DISCOUNT_NO_LONGER_VALID"));
        assertThat(paymentRepository.countByBookingId(bookingId)).isZero();

        mockMvc.perform(delete("/api/bookings/{id}/discount", bookingId).with(bearer(alice))).andExpect(status().isOk());
        pay(alice, bookingId, newKey(), "tok_success")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(200.00));
    }

    @Test
    void fullyDiscountedBooking_isConfirmedWithoutCharging() throws Exception {
        discountCodeRepository.save(new DiscountCode("FREE", DiscountType.PERCENT, new BigDecimal("100")));
        long bookingId = idOf(hold(alice, show.seat("A1")));
        applyCode(alice, bookingId, "FREE").andExpect(jsonPath("$.totalAmount").value(0));

        pay(alice, bookingId, newKey(), "tok_declined") // token is ignored: nothing is charged
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(0))
                .andExpect(jsonPath("$.method").value("NONE"))
                .andExpect(jsonPath("$.gatewayReference").doesNotExist())
                .andExpect(jsonPath("$.booking.status").value("CONFIRMED"));
    }

    private static String newKey() {
        return "test-" + UUID.randomUUID();
    }

    private ResultActions pay(String token, long bookingId, String key, String paymentToken) throws Exception {
        return mockMvc.perform(post("/api/bookings/{id}/pay", bookingId).with(bearer(token))
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"method\": \"CARD\", \"paymentToken\": \"" + paymentToken + "\"}"));
    }

    private ResultActions applyCode(String token, long bookingId, String code) throws Exception {
        return mockMvc.perform(post("/api/bookings/{id}/discount", bookingId).with(bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content("{\"code\": \"" + code + "\"}"));
    }

    private ResultActions hold(String token, long... seats) throws Exception {
        String ids = LongStream.of(seats).mapToObj(String::valueOf).collect(Collectors.joining(", "));
        return mockMvc.perform(post("/api/shows/{id}/holds", show.showId()).with(bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content("{\"showSeatIds\": [" + ids + "]}"));
    }

    private static long idOf(ResultActions result) throws Exception {
        String json = result.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }
}
