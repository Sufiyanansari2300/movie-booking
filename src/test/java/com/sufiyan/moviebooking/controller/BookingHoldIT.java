package com.sufiyan.moviebooking.controller;

import com.jayway.jsonpath.JsonPath;
import com.sufiyan.moviebooking.entity.BookingStatus;
import com.sufiyan.moviebooking.entity.Role;
import com.sufiyan.moviebooking.entity.ShowSeatStatus;
import com.sufiyan.moviebooking.entity.User;
import com.sufiyan.moviebooking.repository.BookingRepository;
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

import java.time.Duration;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import static com.sufiyan.moviebooking.support.AuthTestSupport.bearer;
import static com.sufiyan.moviebooking.support.AuthTestSupport.login;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
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
class BookingHoldIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private ApplicationContext ctx;
    @Autowired private MutableClock clock;
    @Autowired private HoldExpiryJob expiryJob;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private ShowSeatRepository showSeatRepository;

    private TestData data;
    private ShowFixture show;
    private String alice;
    private String bob;
    private String admin;

    @BeforeEach
    void setUp() throws Exception {
        clock.reset();
        data = new TestData(ctx);
        show = data.show();
        alice = token(data.user(Role.CUSTOMER));
        bob = token(data.user(Role.CUSTOMER));
        admin = token(data.user(Role.ADMIN));
    }

    @Test
    void holdSeats_reservesThemAndPricesBySeatType() throws Exception {
        hold(alice, show.seat("A1"), show.seat("C1"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("HELD"))
                .andExpect(jsonPath("$.seats", hasSize(2)))
                .andExpect(jsonPath("$.seats[0].label").value("A1"))
                .andExpect(jsonPath("$.seats[0].price").value(200.00))
                .andExpect(jsonPath("$.seats[1].label").value("C1"))
                .andExpect(jsonPath("$.seats[1].seatType").value("PREMIUM"))
                .andExpect(jsonPath("$.totalAmount").value(550.00))
                .andExpect(jsonPath("$.holdExpiresAt").isString());

        mockMvc.perform(get("/api/shows/{id}/seats", show.showId()))
                .andExpect(jsonPath("$.summary.HELD").value(2))
                .andExpect(jsonPath("$.summary.AVAILABLE").value(13))
                .andExpect(jsonPath("$.rows[0].seats[0].status").value("HELD"));
        mockMvc.perform(get("/api/shows/{id}", show.showId()))
                .andExpect(jsonPath("$.availableSeats").value(13));
    }

    @Test
    void seatHeldBySomeoneElse_cannotBeHeld_andNothingIsPartiallyHeld() throws Exception {
        hold(alice, show.seat("A1")).andExpect(status().isCreated());

        hold(bob, show.seat("A2"), show.seat("A1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("SEATS_UNAVAILABLE"))
                .andExpect(jsonPath("$.message", containsString("A1")));

        // All or nothing: A2 must still be free
        assertThat(showSeatRepository.findById(show.seat("A2")).orElseThrow().getStatus())
                .isEqualTo(ShowSeatStatus.AVAILABLE);
    }

    @Test
    void invalidHoldRequests_areRejected() throws Exception {
        hold(alice, show.seat("A1"), show.seat("A1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("DUPLICATE_SEATS"));

        long[] eleven = show.seats().stream().mapToLong(s -> s.getId()).limit(11).toArray();
        hold(alice, eleven)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("TOO_MANY_SEATS"));

        ShowFixture other = data.show();
        hold(alice, other.seat("A1"))  // seat of another show, posted to this show
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("SEAT_NOT_IN_SHOW"));

        mockMvc.perform(post("/api/shows/{id}/holds", show.showId()).with(bearer(alice))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"showSeatIds\": []}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"));

        mockMvc.perform(post("/api/shows/{id}/holds", 999999).with(bearer(alice))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"showSeatIds\": [1]}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void holdingRequiresLogin() throws Exception {
        mockMvc.perform(post("/api/shows/{id}/holds", show.showId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"showSeatIds\": [%d]}".formatted(show.seat("A1"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void oneActiveHoldPerCustomerPerShow() throws Exception {
        hold(alice, show.seat("A1")).andExpect(status().isCreated());

        hold(alice, show.seat("A2"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("ACTIVE_HOLD_EXISTS"));

        // A different show is fine
        ShowFixture other = data.show();
        mockMvc.perform(post("/api/shows/{id}/holds", other.showId()).with(bearer(alice))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"showSeatIds\": [%d]}".formatted(other.seat("A1"))))
                .andExpect(status().isCreated());
    }

    @Test
    void releaseHold_freesSeats_andCanOnlyHappenOnce() throws Exception {
        long bookingId = idOf(hold(alice, show.seat("A1"), show.seat("A2")));

        mockMvc.perform(delete("/api/bookings/{id}/hold", bookingId).with(bearer(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RELEASED"));
        mockMvc.perform(get("/api/shows/{id}/seats", show.showId()))
                .andExpect(jsonPath("$.summary.HELD").value(0))
                .andExpect(jsonPath("$.summary.AVAILABLE").value(15));

        mockMvc.perform(delete("/api/bookings/{id}/hold", bookingId).with(bearer(alice)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("BOOKING_NOT_HELD"));

        // Released seats can be held again, and Alice may start a new hold
        hold(bob, show.seat("A1")).andExpect(status().isCreated());
        hold(alice, show.seat("A2")).andExpect(status().isCreated());
    }

    @Test
    void bookingsArePrivate_toOwnerAndAdmins() throws Exception {
        long bookingId = idOf(hold(alice, show.seat("A1")));

        mockMvc.perform(get("/api/bookings/{id}", bookingId).with(bearer(alice)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/bookings/{id}", bookingId).with(bearer(bob)))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/bookings/{id}/hold", bookingId).with(bearer(bob)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/bookings/{id}", bookingId).with(bearer(admin)))
                .andExpect(status().isOk());
    }

    @Test
    void expiredHold_isReportedExpired_andItsSeatsAreFreeBeforeTheSweeperRuns() throws Exception {
        long bookingId = idOf(hold(alice, show.seat("A1")));

        clock.advance(Duration.ofMinutes(11));

        mockMvc.perform(get("/api/bookings/{id}", bookingId).with(bearer(alice)))
                .andExpect(jsonPath("$.status").value("EXPIRED"));
        mockMvc.perform(get("/api/shows/{id}/seats", show.showId()))
                .andExpect(jsonPath("$.summary.AVAILABLE").value(15))
                .andExpect(jsonPath("$.rows[0].seats[0].status").value("AVAILABLE"));
        // Alice's expired hold does not count as active any more
        hold(alice, show.seat("B1")).andExpect(status().isCreated());
    }

    @Test
    void takeoverOfExpiredSeat_isNotUndoneWhenSweeperExpiresTheOldBooking() throws Exception {
        long aliceBooking = idOf(hold(alice, show.seat("A1")));
        clock.advance(Duration.ofMinutes(11));

        // Bob takes the seat before the sweeper has processed Alice's booking
        long bobBooking = idOf(hold(bob, show.seat("A1")));

        assertThat(expiryJob.sweep()).isEqualTo(1);

        assertThat(bookingRepository.findById(aliceBooking).orElseThrow().getStatus()).isEqualTo(BookingStatus.EXPIRED);
        assertThat(bookingRepository.findById(bobBooking).orElseThrow().getStatus()).isEqualTo(BookingStatus.HELD);
        var seat = showSeatRepository.findById(show.seat("A1")).orElseThrow();
        assertThat(seat.getStatus()).isEqualTo(ShowSeatStatus.HELD);
        assertThat(seat.getBooking().getId()).isEqualTo(bobBooking);
    }

    @Test
    void sweeper_expiresOverdueHoldsAndFreesSeats_butLeavesActiveOnesAlone() throws Exception {
        long overdue = idOf(hold(alice, show.seat("A1"), show.seat("A2")));
        clock.advance(Duration.ofMinutes(6));
        long active = idOf(hold(bob, show.seat("B1")));
        clock.advance(Duration.ofMinutes(5)); // Alice's hold is 11 min old, Bob's 5 min

        assertThat(expiryJob.sweep()).isEqualTo(1);
        assertThat(expiryJob.sweep()).isZero(); // idempotent

        assertThat(bookingRepository.findById(overdue).orElseThrow().getStatus()).isEqualTo(BookingStatus.EXPIRED);
        assertThat(bookingRepository.findById(active).orElseThrow().getStatus()).isEqualTo(BookingStatus.HELD);
        assertThat(showSeatRepository.findById(show.seat("A1")).orElseThrow().getStatus()).isEqualTo(ShowSeatStatus.AVAILABLE);
        assertThat(showSeatRepository.findById(show.seat("A1")).orElseThrow().getBooking()).isNull();
        assertThat(showSeatRepository.findById(show.seat("B1")).orElseThrow().getStatus()).isEqualTo(ShowSeatStatus.HELD);
    }

    @Test
    void showThatHasStarted_cannotBeHeld() throws Exception {
        clock.advance(Duration.ofDays(2)); // the fixture show was tomorrow evening

        hold(alice, show.seat("A1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("SHOW_NOT_BOOKABLE"));
    }

    @Test
    void showWithBookingHistory_cannotBeDeleted() throws Exception {
        long bookingId = idOf(hold(alice, show.seat("A1")));
        mockMvc.perform(delete("/api/bookings/{id}/hold", bookingId).with(bearer(alice)))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/admin/shows/{id}", show.showId()).with(bearer(admin)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("SHOW_HAS_BOOKINGS"));
    }

    private String token(User user) throws Exception {
        return login(mockMvc, user.getEmail(), TestData.PASSWORD);
    }

    private ResultActions hold(String token, long... showSeatIds) throws Exception {
        String ids = LongStream.of(showSeatIds).mapToObj(String::valueOf).collect(Collectors.joining(", "));
        return mockMvc.perform(post("/api/shows/{id}/holds", show.showId()).with(bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"showSeatIds\": [" + ids + "]}"));
    }

    private static long idOf(ResultActions result) throws Exception {
        String json = result.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }
}
