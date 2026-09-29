package com.sufiyan.moviebooking.concurrency;

import com.sufiyan.moviebooking.entity.Booking;
import com.sufiyan.moviebooking.entity.BookingSeat;
import com.sufiyan.moviebooking.entity.BookingStatus;
import com.sufiyan.moviebooking.entity.Role;
import com.sufiyan.moviebooking.entity.ShowSeat;
import com.sufiyan.moviebooking.entity.ShowSeatStatus;
import com.sufiyan.moviebooking.exception.BusinessException;
import com.sufiyan.moviebooking.repository.BookingRepository;
import com.sufiyan.moviebooking.repository.BookingSeatRepository;
import com.sufiyan.moviebooking.repository.ShowSeatRepository;
import com.sufiyan.moviebooking.service.BookingService;
import com.sufiyan.moviebooking.support.TestData;
import com.sufiyan.moviebooking.support.TestData.ShowFixture;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real concurrent requests against a real database (no test transaction, data is committed).
 * Subclasses pick the database: H2 always, MySQL when configured.
 */
abstract class BookingConcurrencyScenarios {

    @Autowired private ApplicationContext ctx;
    @Autowired private BookingService bookingService;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private BookingSeatRepository bookingSeatRepository;
    @Autowired private ShowSeatRepository showSeatRepository;

    @Test
    void twentyCustomersRaceForOneSeat_exactlyOneWins() throws Exception {
        TestData data = new TestData(ctx);
        ShowFixture show = data.show();
        long seat = show.seat("A1");
        List<Long> customers = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            customers.add(data.user(Role.CUSTOMER).getId());
        }

        List<Outcome> outcomes = runConcurrently(customers.stream()
                .map(userId -> (Callable<Long>) () -> bookingService.hold(userId, show.showId(), List.of(seat)).id())
                .toList());

        List<Long> winners = outcomes.stream().filter(Outcome::won).map(Outcome::bookingId).toList();
        assertThat(winners).as("successful holds").hasSize(1);
        assertThat(outcomes).filteredOn(o -> !o.won())
                .as("every loser is told the seat is taken")
                .hasSize(19)
                .allSatisfy(o -> assertThat(o.errorCode()).isEqualTo("SEATS_UNAVAILABLE"));

        ShowSeat stored = showSeatRepository.findById(seat).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(ShowSeatStatus.HELD);
        assertThat(bookingIdOf(stored)).isEqualTo(winners.getFirst());
    }

    /**
     * Customers request overlapping pairs of seats, half of them in reverse order, to provoke lock-order
     * deadlocks. Everyone must get an answer, and no seat may end up in two live bookings.
     */
    @Test
    void overlappingMultiSeatHolds_neverDoubleAllocate_andDoNotDeadlock() throws Exception {
        TestData data = new TestData(ctx);
        ShowFixture show = data.show();
        List<Long> row = List.of(show.seat("A1"), show.seat("A2"), show.seat("A3"), show.seat("A4"), show.seat("A5"));
        List<Callable<Long>> tasks = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            long userId = data.user(Role.CUSTOMER).getId();
            long a = row.get(i % 4);
            long b = row.get(i % 4 + 1);
            List<Long> seats = i % 2 == 0 ? List.of(a, b) : List.of(b, a);
            tasks.add(() -> bookingService.hold(userId, show.showId(), seats).id());
        }

        List<Outcome> outcomes = runConcurrently(tasks);

        assertThat(outcomes).filteredOn(o -> !o.won())
                .allSatisfy(o -> assertThat(o.errorCode()).as("only 'seat taken' failures, no deadlocks")
                        .isEqualTo("SEATS_UNAVAILABLE"));
        List<Long> winners = outcomes.stream().filter(Outcome::won).map(Outcome::bookingId).toList();
        assertThat(winners).as("at least one hold succeeds").isNotEmpty();

        Map<Long, Long> seatOwner = new HashMap<>();
        for (Long bookingId : winners) {
            Booking booking = bookingRepository.findById(bookingId).orElseThrow();
            assertThat(booking.getStatus()).isEqualTo(BookingStatus.HELD);
            for (BookingSeat bs : bookingSeatRepository.findByBookingId(bookingId)) {
                Long previous = seatOwner.put(bs.getShowSeat().getId(), bookingId);
                assertThat(previous).as("seat %d allocated twice", bs.getShowSeat().getId()).isNull();
                ShowSeat stored = showSeatRepository.findById(bs.getShowSeat().getId()).orElseThrow();
                assertThat(bookingIdOf(stored)).isEqualTo(bookingId);
            }
        }
    }

    /** Id of the booking that currently holds the seat (reading a lazy proxy's id needs no session). */
    private static Long bookingIdOf(ShowSeat seat) {
        return seat.getBooking() == null ? null : seat.getBooking().getId();
    }

    private record Outcome(Long bookingId, String errorCode) {
        boolean won() {
            return bookingId != null;
        }
    }

    /** Starts all tasks at the same instant and collects each result or business error. */
    private static List<Outcome> runConcurrently(List<Callable<Long>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch ready = new CountDownLatch(tasks.size());
        CountDownLatch go = new CountDownLatch(1);
        try {
            List<Future<Outcome>> futures = new ArrayList<>();
            for (Callable<Long> task : tasks) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    try {
                        return new Outcome(task.call(), null);
                    } catch (BusinessException e) {
                        return new Outcome(null, e.getErrorCode());
                    } catch (RuntimeException e) {
                        return new Outcome(null, e.getClass().getSimpleName() + ": " + e.getMessage());
                    }
                }));
            }
            ready.await(10, TimeUnit.SECONDS);
            go.countDown();
            List<Outcome> outcomes = new ArrayList<>();
            for (Future<Outcome> f : futures) {
                outcomes.add(f.get(60, TimeUnit.SECONDS)); // a deadlock would surface as a timeout here
            }
            return outcomes;
        } finally {
            pool.shutdownNow();
        }
    }
}
