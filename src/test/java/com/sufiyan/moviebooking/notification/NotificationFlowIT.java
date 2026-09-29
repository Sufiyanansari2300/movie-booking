package com.sufiyan.moviebooking.notification;

import com.sufiyan.moviebooking.dto.PayRequest;
import com.sufiyan.moviebooking.entity.Notification;
import com.sufiyan.moviebooking.entity.NotificationStatus;
import com.sufiyan.moviebooking.entity.NotificationType;
import com.sufiyan.moviebooking.entity.PaymentMethod;
import com.sufiyan.moviebooking.entity.Role;
import com.sufiyan.moviebooking.entity.User;
import com.sufiyan.moviebooking.repository.NotificationRepository;
import com.sufiyan.moviebooking.security.AppUserPrincipal;
import com.sufiyan.moviebooking.service.BookingService;
import com.sufiyan.moviebooking.service.PaymentService;
import com.sufiyan.moviebooking.support.MutableClock;
import com.sufiyan.moviebooking.support.MutableClockConfig;
import com.sufiyan.moviebooking.support.RecordingNotificationSender;
import com.sufiyan.moviebooking.support.TestData;
import com.sufiyan.moviebooking.support.TestData.ShowFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.sufiyan.moviebooking.support.AuthTestSupport.bearer;
import static com.sufiyan.moviebooking.support.AuthTestSupport.login;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Real commits and the real async executor (after-commit listeners never fire inside a rolled-back test
 * transaction), so this test uses its own database.
 */
@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:notifications;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@Import({MutableClockConfig.class, NotificationFlowIT.SenderConfig.class})
class NotificationFlowIT {

    @TestConfiguration
    static class SenderConfig {
        @Bean
        @Primary
        RecordingNotificationSender recordingSender() {
            return new RecordingNotificationSender();
        }
    }

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final PayRequest CARD = new PayRequest(PaymentMethod.CARD, "tok_success");

    @Autowired private ApplicationContext ctx;
    @Autowired private MockMvc mockMvc;
    @Autowired private MutableClock clock;
    @Autowired private RecordingNotificationSender sender;
    @Autowired private BookingService bookingService;
    @Autowired private PaymentService paymentService;
    @Autowired private NotificationService notificationService;
    @Autowired private NotificationJobs jobs;
    @Autowired private NotificationRepository notificationRepository;

    private TestData data;

    @BeforeEach
    void setUp() {
        clock.reset();
        sender.reset();
        data = new TestData(ctx);
    }

    @Test
    void confirmationIsSentInTheBackground_afterPayment_withoutBlockingIt() {
        User user = data.user(Role.CUSTOMER);
        ShowFixture show = data.show();
        long bookingId = hold(user, show, "A1", "A2");
        sender.close(); // the "email provider" hangs

        long started = System.nanoTime();
        var outcome = paymentService.pay(AppUserPrincipal.from(user), bookingId, key(), CARD);
        long tookMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);

        assertThat(outcome.payment().booking().status().name()).isEqualTo("CONFIRMED");
        assertThat(tookMs).as("payment must not wait for the provider").isLessThan(3000);
        await().atMost(Duration.ofSeconds(5)).until(() -> !sender.threads.isEmpty());
        assertThat(sender.sent).isEmpty(); // still stuck in the provider

        sender.open();
        Notification n = awaitNotification(bookingId, NotificationType.BOOKING_CONFIRMED, NotificationStatus.SENT);
        assertThat(n.getRecipient()).isEqualTo(user.getEmail());
        assertThat(n.getSubject()).startsWith("Booking #" + bookingId + " confirmed");
        assertThat(n.getBody()).contains("A1, A2").contains("INR 400.00");
        assertThat(sender.threads).allSatisfy(t -> assertThat(t).startsWith("notify-"));
    }

    @Test
    void declinedPayment_sendsNothing() throws Exception {
        User user = data.user(Role.CUSTOMER);
        long bookingId = hold(user, data.show(), "A1");

        paymentService.pay(AppUserPrincipal.from(user), bookingId, key(), new PayRequest(PaymentMethod.CARD, "tok_declined"));

        Thread.sleep(500);
        assertThat(notificationRepository.findByBookingIdOrderByCreatedAtAsc(bookingId)).isEmpty();
        assertThat(sender.sent).isEmpty();
    }

    @Test
    void providerFailure_neverAffectsTheBooking_andIsRetriedUpToTheLimit() {
        User user = data.user(Role.CUSTOMER);
        long bookingId = hold(user, data.show(), "A1");
        sender.failNext(1);

        var outcome = paymentService.pay(AppUserPrincipal.from(user), bookingId, key(), CARD);

        assertThat(outcome.payment().booking().status().name()).isEqualTo("CONFIRMED");
        Notification failed = awaitNotification(bookingId, NotificationType.BOOKING_CONFIRMED, NotificationStatus.FAILED);
        assertThat(failed.getAttempts()).isEqualTo(1);
        assertThat(failed.getLastError()).contains("provider down");

        jobs.retryFailed();

        Notification sent = awaitNotification(bookingId, NotificationType.BOOKING_CONFIRMED, NotificationStatus.SENT);
        assertThat(sent.getAttempts()).isEqualTo(2);
    }

    @Test
    void permanentlyFailingNotification_stopsBeingRetriedAtMaxAttempts() {
        User user = data.user(Role.CUSTOMER);
        long bookingId = hold(user, data.show(), "A1");
        sender.failNext(100);
        paymentService.pay(AppUserPrincipal.from(user), bookingId, key(), CARD);
        awaitNotification(bookingId, NotificationType.BOOKING_CONFIRMED, NotificationStatus.FAILED);

        for (int i = 0; i < 5; i++) {
            jobs.retryFailed();
        }

        Notification n = notificationRepository.findByBookingIdOrderByCreatedAtAsc(bookingId).getFirst();
        assertThat(n.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(n.getAttempts()).isEqualTo(3);
    }

    @Test
    void sameNotificationRequestedConcurrently_isRecordedAndSentOnce() throws Exception {
        User user = data.user(Role.CUSTOMER);
        long bookingId = hold(user, data.show(), "A1");
        paymentService.pay(AppUserPrincipal.from(user), bookingId, key(), CARD);
        awaitNotification(bookingId, NotificationType.BOOKING_CONFIRMED, NotificationStatus.SENT);

        ExecutorService pool = Executors.newFixedThreadPool(5);
        for (int i = 0; i < 5; i++) {
            pool.submit(() -> notificationService.notify(bookingId, NotificationType.SHOW_REMINDER));
        }
        pool.shutdown();
        assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

        assertThat(notificationRepository.findByBookingIdOrderByCreatedAtAsc(bookingId))
                .extracting(Notification::getType)
                .containsExactlyInAnyOrder(NotificationType.BOOKING_CONFIRMED, NotificationType.SHOW_REMINDER);
        assertThat(sender.sent).filteredOn(m -> m.subject().startsWith("Reminder")).hasSize(1);
    }

    @Test
    void reminderGoesOutOnceWithinTheLeadTime_onlyForConfirmedBookings() {
        // Show tomorrow 18:00; reminders go out 2h before
        ShowFixture show = data.show();
        User paid = data.user(Role.CUSTOMER);
        long paidBooking = hold(paid, show, "A1");
        paymentService.pay(AppUserPrincipal.from(paid), paidBooking, key(), CARD);
        long unpaidBooking = hold(data.user(Role.CUSTOMER), show, "A2");
        awaitNotification(paidBooking, NotificationType.BOOKING_CONFIRMED, NotificationStatus.SENT);

        assertThat(jobs.sendReminders()).as("too early").isZero();

        Instant oneHourBefore = LocalDate.now(IST).plusDays(1).atTime(17, 0).atZone(IST).toInstant();
        clock.advance(Duration.between(clock.instant(), oneHourBefore));

        assertThat(jobs.sendReminders()).isEqualTo(1);
        assertThat(jobs.sendReminders()).as("already reminded").isZero();
        Notification reminder = awaitNotification(paidBooking, NotificationType.SHOW_REMINDER, NotificationStatus.SENT);
        assertThat(reminder.getSubject()).startsWith("Reminder:");
        assertThat(notificationRepository.findByBookingIdOrderByCreatedAtAsc(unpaidBooking)).isEmpty();
    }

    @Test
    void notificationsCanBeViewedByTheOwnerAndAdmins() throws Exception {
        User alice = data.user(Role.CUSTOMER);
        long bookingId = hold(alice, data.show(), "A1");
        paymentService.pay(AppUserPrincipal.from(alice), bookingId, key(), CARD);
        awaitNotification(bookingId, NotificationType.BOOKING_CONFIRMED, NotificationStatus.SENT);

        mockMvc.perform(get("/api/bookings/{id}/notifications", bookingId)
                        .with(bearer(login(mockMvc, alice.getEmail(), TestData.PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].type").value("BOOKING_CONFIRMED"))
                .andExpect(jsonPath("$[0].status").value("SENT"));
        mockMvc.perform(get("/api/bookings/{id}/notifications", bookingId)
                        .with(bearer(login(mockMvc, data.user(Role.CUSTOMER).getEmail(), TestData.PASSWORD))))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/admin/notifications").param("status", "SENT")
                        .with(bearer(login(mockMvc, data.user(Role.ADMIN).getEmail(), TestData.PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").isNumber());
    }

    private long hold(User user, ShowFixture show, String... seats) {
        return bookingService.hold(user.getId(), show.showId(), List.of(seats).stream().map(show::seat).toList()).id();
    }

    private Notification awaitNotification(long bookingId, NotificationType type, NotificationStatus status) {
        return await().atMost(Duration.ofSeconds(5)).until(
                () -> notificationRepository.findByBookingIdOrderByCreatedAtAsc(bookingId).stream()
                        .filter(n -> n.getType() == type && n.getStatus() == status).findFirst().orElse(null),
                n -> n != null);
    }

    private static String key() {
        return "k-" + UUID.randomUUID();
    }
}
