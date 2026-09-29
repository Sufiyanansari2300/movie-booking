package com.sufiyan.moviebooking.controller;

import com.jayway.jsonpath.JsonPath;
import com.sufiyan.moviebooking.entity.BookingStatus;
import com.sufiyan.moviebooking.entity.Role;
import com.sufiyan.moviebooking.entity.ShowSeatStatus;
import com.sufiyan.moviebooking.payment.MockPaymentGateway;
import com.sufiyan.moviebooking.payment.PaymentGateway.RefundResult;
import com.sufiyan.moviebooking.repository.BookingRepository;
import com.sufiyan.moviebooking.repository.RefundRepository;
import com.sufiyan.moviebooking.repository.ShowSeatRepository;
import com.sufiyan.moviebooking.support.TestData;
import com.sufiyan.moviebooking.support.TestData.ShowFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static com.sufiyan.moviebooking.support.AuthTestSupport.bearer;
import static com.sufiyan.moviebooking.support.AuthTestSupport.login;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** When the provider cannot refund, nothing changes and the cancellation can be retried. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RefundFailureIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private ApplicationContext ctx;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private ShowSeatRepository showSeatRepository;
    @Autowired private RefundRepository refundRepository;

    @MockitoSpyBean
    private MockPaymentGateway gateway;

    private ShowFixture show;
    private String alice;
    private String admin;

    @BeforeEach
    void setUp() throws Exception {
        TestData data = new TestData(ctx);
        show = data.show();
        alice = login(mockMvc, data.user(Role.CUSTOMER).getEmail(), TestData.PASSWORD);
        admin = login(mockMvc, data.user(Role.ADMIN).getEmail(), TestData.PASSWORD);
        mockMvc.perform(post("/api/admin/refund-policies").with(bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"Full\", \"active\": true, \"rules\": [{\"minHoursBeforeShow\": 0, \"refundPercent\": 100}]}"))
                .andExpect(status().isCreated());
    }

    @Test
    void failedRefund_leavesTheBookingConfirmed_andCanBeRetried() throws Exception {
        long bookingId = paid("A1");
        doReturn(new RefundResult(false, null, "provider timeout")).when(gateway).refund(any());

        mockMvc.perform(post("/api/bookings/{id}/cancel", bookingId).with(bearer(alice)))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("REFUND_FAILED"));
        assertThat(bookingRepository.findById(bookingId).orElseThrow().getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(showSeatRepository.findById(show.seat("A1")).orElseThrow().getStatus()).isEqualTo(ShowSeatStatus.BOOKED);
        assertThat(refundRepository.findByBookingId(bookingId)).isEmpty();

        doCallRealMethod().when(gateway).refund(any());
        mockMvc.perform(post("/api/bookings/{id}/cancel", bookingId).with(bearer(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refund.amount").value(200.00));
    }

    @Test
    void showCancellation_reportsBookingsItCouldNotRefund_andResumesThemOnRerun() throws Exception {
        long bookingId = paid("A1");
        doReturn(new RefundResult(false, null, "provider timeout")).when(gateway).refund(any());

        cancelShow()
                .andExpect(jsonPath("$.refundedBookings").value(0))
                .andExpect(jsonPath("$.failedBookingIds", contains((int) bookingId)));
        assertThat(bookingRepository.findById(bookingId).orElseThrow().getStatus()).isEqualTo(BookingStatus.CONFIRMED);

        doCallRealMethod().when(gateway).refund(any());
        cancelShow()
                .andExpect(jsonPath("$.refundedBookings").value(1))
                .andExpect(jsonPath("$.totalRefunded").value(200.00))
                .andExpect(jsonPath("$.failedBookingIds").isEmpty());
    }

    private ResultActions cancelShow() throws Exception {
        return mockMvc.perform(post("/api/admin/shows/{id}/cancel", show.showId()).with(bearer(admin))
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\": \"Power cut\"}"));
    }

    private long paid(String seat) throws Exception {
        String json = mockMvc.perform(post("/api/shows/{id}/holds", show.showId()).with(bearer(alice))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"showSeatIds\": [" + show.seat(seat) + "]}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(json, "$.id")).longValue();
        mockMvc.perform(post("/api/bookings/{id}/pay", id).with(bearer(alice)).header("Idempotency-Key", "k-" + UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON).content("{\"method\": \"CARD\"}")).andExpect(status().isOk());
        return id;
    }
}
