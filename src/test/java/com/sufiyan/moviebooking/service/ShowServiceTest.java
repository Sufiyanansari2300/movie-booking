package com.sufiyan.moviebooking.service;

import com.sufiyan.moviebooking.config.ShowProperties;
import com.sufiyan.moviebooking.dto.CreateShowRequest;
import com.sufiyan.moviebooking.entity.City;
import com.sufiyan.moviebooking.entity.Movie;
import com.sufiyan.moviebooking.entity.Screen;
import com.sufiyan.moviebooking.entity.Seat;
import com.sufiyan.moviebooking.entity.SeatType;
import com.sufiyan.moviebooking.entity.Show;
import com.sufiyan.moviebooking.entity.Theater;
import com.sufiyan.moviebooking.exception.BadRequestException;
import com.sufiyan.moviebooking.exception.ConflictException;
import com.sufiyan.moviebooking.repository.BookingRepository;
import com.sufiyan.moviebooking.repository.PricingRuleRepository;
import com.sufiyan.moviebooking.repository.ScreenRepository;
import com.sufiyan.moviebooking.repository.SeatRepository;
import com.sufiyan.moviebooking.repository.ShowPriceRepository;
import com.sufiyan.moviebooking.repository.ShowRepository;
import com.sufiyan.moviebooking.repository.ShowSeatRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ShowServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-01T06:00:00Z");
    private static final OffsetDateTime TOMORROW_6PM = OffsetDateTime.parse("2026-10-02T18:00:00+05:30");

    @Mock private ShowRepository showRepository;
    @Mock private ShowPriceRepository showPriceRepository;
    @Mock private ShowSeatRepository showSeatRepository;
    @Mock private ScreenRepository screenRepository;
    @Mock private SeatRepository seatRepository;
    @Mock private MovieService movieService;
    @Mock private BookingRepository bookingRepository;
    @Mock private PricingRuleRepository pricingRuleRepository;

    private ShowService service;
    private Screen screen;

    @BeforeEach
    void setUp() {
        service = new ShowService(showRepository, showPriceRepository, showSeatRepository, screenRepository,
                seatRepository, bookingRepository, movieService, new PricingService(pricingRuleRepository, ZoneId.of("Asia/Kolkata")), new ShowProperties(Duration.ofMinutes(15)),
                Clock.fixed(NOW, ZoneOffset.UTC), ZoneId.of("Asia/Kolkata"));
        screen = new Screen(new Theater(new City("Pune", "MH"), "Riverside", "KP"), "Audi 1");
        when(movieService.getEntity(1L)).thenReturn(new Movie("Inception", null, "English", "Sci-Fi", 148, "UA", null));
        when(screenRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(screen));
        when(seatRepository.findByScreenIdOrderByRowLabelAscSeatNumberAsc(any())).thenReturn(List.of(
                new Seat(screen, "A", 1, SeatType.REGULAR), new Seat(screen, "J", 1, SeatType.PREMIUM)));
        when(showRepository.save(any(Show.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void create_endTimeIsStartPlusDurationPlusCleanupBuffer() {
        AtomicReference<Show> saved = new AtomicReference<>();
        when(showRepository.save(any(Show.class))).thenAnswer(inv -> {
            saved.set(inv.getArgument(0));
            return saved.get();
        });
        when(showRepository.findDetailedById(any())).thenAnswer(inv -> Optional.of(saved.get()));

        service.create(request(TOMORROW_6PM, Map.of(SeatType.REGULAR, bd("200"), SeatType.PREMIUM, bd("350"))));

        assertThat(saved.get().getEndTime())
                .isEqualTo(TOMORROW_6PM.toInstant().plus(Duration.ofMinutes(148 + 15)));
    }

    @Test
    void create_rejectsStartInThePast() {
        assertThatThrownBy(() -> service.create(request(OffsetDateTime.parse("2026-10-01T10:00:00+05:30"),
                Map.of(SeatType.REGULAR, bd("200"), SeatType.PREMIUM, bd("350")))))
                .isInstanceOf(BadRequestException.class)
                .extracting("errorCode").isEqualTo("SHOW_IN_PAST");
        verify(showRepository, never()).save(any());
    }

    @Test
    void create_requiresPriceForEverySeatTypeOnTheScreen() {
        assertThatThrownBy(() -> service.create(request(TOMORROW_6PM, Map.of(SeatType.REGULAR, bd("200")))))
                .isInstanceOf(BadRequestException.class)
                .extracting("errorCode").isEqualTo("MISSING_PRICE");
    }

    @Test
    void create_rejectsOverlap() {
        when(showRepository.existsOverlapping(any(), any(), any())).thenReturn(true);

        assertThatThrownBy(() -> service.create(request(TOMORROW_6PM,
                Map.of(SeatType.REGULAR, bd("200"), SeatType.PREMIUM, bd("350")))))
                .isInstanceOf(ConflictException.class)
                .extracting("errorCode").isEqualTo("SHOW_OVERLAP");
        verify(showRepository, never()).save(any());
    }

    @Test
    void create_rejectsScreenWithoutSeats() {
        when(seatRepository.findByScreenIdOrderByRowLabelAscSeatNumberAsc(any())).thenReturn(List.of());

        assertThatThrownBy(() -> service.create(request(TOMORROW_6PM, Map.of(SeatType.REGULAR, bd("200")))))
                .isInstanceOf(BadRequestException.class)
                .extracting("errorCode").isEqualTo("SCREEN_HAS_NO_SEATS");
    }

    private static CreateShowRequest request(OffsetDateTime start, Map<SeatType, BigDecimal> prices) {
        return new CreateShowRequest(1L, 2L, start, prices);
    }

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }
}
