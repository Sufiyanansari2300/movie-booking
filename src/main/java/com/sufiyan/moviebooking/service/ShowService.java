package com.sufiyan.moviebooking.service;

import com.sufiyan.moviebooking.config.ShowProperties;
import com.sufiyan.moviebooking.dto.CreateShowRequest;
import com.sufiyan.moviebooking.dto.PageResponse;
import com.sufiyan.moviebooking.dto.SeatMapResponse;
import com.sufiyan.moviebooking.dto.ShowResponse;
import com.sufiyan.moviebooking.dto.UpdateShowPricesRequest;
import com.sufiyan.moviebooking.entity.Movie;
import com.sufiyan.moviebooking.entity.Screen;
import com.sufiyan.moviebooking.entity.Seat;
import com.sufiyan.moviebooking.entity.SeatType;
import com.sufiyan.moviebooking.entity.Show;
import com.sufiyan.moviebooking.entity.ShowPrice;
import com.sufiyan.moviebooking.entity.ShowSeat;
import com.sufiyan.moviebooking.entity.ShowSeatStatus;
import com.sufiyan.moviebooking.entity.ShowStatus;
import com.sufiyan.moviebooking.entity.Theater;
import com.sufiyan.moviebooking.exception.BadRequestException;
import com.sufiyan.moviebooking.exception.ConflictException;
import com.sufiyan.moviebooking.exception.ResourceNotFoundException;
import com.sufiyan.moviebooking.repository.ScreenRepository;
import com.sufiyan.moviebooking.repository.SeatRepository;
import com.sufiyan.moviebooking.repository.ShowPriceRepository;
import com.sufiyan.moviebooking.repository.ShowRepository;
import com.sufiyan.moviebooking.repository.ShowSeatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ShowService {

    private static final Set<String> SORTABLE = Set.of("startTime");

    private final ShowRepository showRepository;
    private final ShowPriceRepository showPriceRepository;
    private final ShowSeatRepository showSeatRepository;
    private final ScreenRepository screenRepository;
    private final SeatRepository seatRepository;
    private final MovieService movieService;
    private final ShowProperties showProperties;
    private final Clock clock;
    private final ZoneId businessZone;

    /**
     * Schedules a show: validates timing and prices, rejects overlaps on the screen, and creates one
     * AVAILABLE show-seat per physical seat.
     */
    @Transactional
    public ShowResponse create(CreateShowRequest request) {
        Movie movie = movieService.getEntity(request.movieId());
        // Lock the screen so two admins cannot schedule overlapping shows concurrently.
        Screen screen = screenRepository.findByIdForUpdate(request.screenId())
                .orElseThrow(() -> new ResourceNotFoundException("Screen", request.screenId()));

        Instant start = request.startTime().toInstant();
        if (!start.isAfter(clock.instant())) {
            throw new BadRequestException("SHOW_IN_PAST", "Show start time must be in the future");
        }
        Instant end = start.plusSeconds(movie.getDurationMinutes() * 60L).plus(showProperties.cleanupBuffer());

        List<Seat> seats = seatRepository.findByScreenIdOrderByRowLabelAscSeatNumberAsc(screen.getId());
        if (seats.isEmpty()) {
            throw new BadRequestException("SCREEN_HAS_NO_SEATS", "Set the screen's seat layout before scheduling shows");
        }
        Map<SeatType, BigDecimal> prices = requirePrices(request.prices(), seatTypes(seats));

        if (showRepository.existsOverlapping(screen.getId(), start, end)) {
            throw new ConflictException("SHOW_OVERLAP", "Another show is scheduled on this screen during that time");
        }

        Show show = showRepository.save(new Show(movie, screen, start, end));
        prices.forEach((type, price) -> showPriceRepository.save(new ShowPrice(show, type, price)));
        showSeatRepository.saveAll(seats.stream().map(seat -> new ShowSeat(show, seat)).toList());
        return get(show.getId());
    }

    /** Changes base prices for future bookings; already confirmed bookings keep the price they paid. */
    @Transactional
    public ShowResponse updatePrices(Long showId, UpdateShowPricesRequest request) {
        Show show = getScheduledUpcoming(showId);
        List<ShowPrice> existing = showPriceRepository.findByShowId(showId);
        Set<SeatType> required = existing.stream().map(ShowPrice::getSeatType)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(SeatType.class)));
        Map<SeatType, BigDecimal> prices = requirePrices(request.prices(), required);
        existing.forEach(p -> p.setPrice(prices.get(p.getSeatType())));
        return get(show.getId());
    }

    /** Removes a show that nobody has booked yet. */
    @Transactional
    public void delete(Long showId) {
        Show show = getEntity(showId);
        if (showSeatRepository.existsByShowIdAndStatusNot(showId, ShowSeatStatus.AVAILABLE)) {
            throw new ConflictException("SHOW_HAS_BOOKINGS", "Seats of this show are held or booked");
        }
        showSeatRepository.deleteByShowId(showId);
        showPriceRepository.deleteByShowId(showId);
        showRepository.deleteById(show.getId());
    }

    @Transactional(readOnly = true)
    public ShowResponse get(Long showId) {
        return toResponses(List.of(getEntity(showId))).getFirst();
    }

    /** Upcoming scheduled shows. {@code date} is a calendar day in the business time zone. */
    @Transactional(readOnly = true)
    public PageResponse<ShowResponse> search(Long cityId, Long theaterId, Long movieId, LocalDate date, Pageable pageable) {
        SortValidator.requireAllowed(pageable, SORTABLE);
        Instant from = date == null ? null : date.atStartOfDay(businessZone).toInstant();
        Instant to = date == null ? null : date.plusDays(1).atStartOfDay(businessZone).toInstant();
        Page<Show> page = showRepository.search(ShowStatus.SCHEDULED, clock.instant(),
                cityId, theaterId, movieId, from, to, pageable);
        List<ShowResponse> content = toResponses(page.getContent());
        return new PageResponse<>(content, page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    @Transactional(readOnly = true)
    public SeatMapResponse seatMap(Long showId) {
        Show show = getEntity(showId);
        Map<SeatType, BigDecimal> prices = pricesOf(showId);
        Map<ShowSeatStatus, Long> summary = new EnumMap<>(ShowSeatStatus.class);
        for (ShowSeatStatus status : ShowSeatStatus.values()) {
            summary.put(status, 0L);
        }
        Map<String, List<ShowSeat>> byRow = new LinkedHashMap<>();
        for (ShowSeat ss : showSeatRepository.findSeatMap(showId)) {
            summary.merge(ss.getStatus(), 1L, Long::sum);
            byRow.computeIfAbsent(ss.getSeat().getRowLabel(), r -> new ArrayList<>()).add(ss);
        }
        List<SeatMapResponse.Row> rows = byRow.entrySet().stream().map(e -> {
            SeatType type = e.getValue().getFirst().getSeat().getSeatType();
            return new SeatMapResponse.Row(e.getKey(), type, prices.get(type), e.getValue().stream()
                    .map(ss -> new SeatMapResponse.SeatStatus(ss.getId(), ss.getSeat().getLabel(),
                            ss.getSeat().getSeatNumber(), ss.getStatus()))
                    .toList());
        }).toList();
        return new SeatMapResponse(show.getId(), toZoned(show.getStartTime()), show.getScreen().getName(), summary, rows);
    }

    Show getEntity(Long showId) {
        return showRepository.findDetailedById(showId).orElseThrow(() -> new ResourceNotFoundException("Show", showId));
    }

    private Show getScheduledUpcoming(Long showId) {
        Show show = getEntity(showId);
        if (show.getStatus() != ShowStatus.SCHEDULED || !show.getStartTime().isAfter(clock.instant())) {
            throw new ConflictException("SHOW_NOT_EDITABLE", "Only upcoming scheduled shows can be changed");
        }
        return show;
    }

    /** Every seat type on the screen needs a price; prices for types the screen does not have are ignored. */
    private static Map<SeatType, BigDecimal> requirePrices(Map<SeatType, BigDecimal> given, Set<SeatType> required) {
        Set<SeatType> missing = EnumSet.noneOf(SeatType.class);
        missing.addAll(required);
        missing.removeAll(given.keySet());
        if (!missing.isEmpty()) {
            throw new BadRequestException("MISSING_PRICE", "Price required for seat types: " + missing);
        }
        Map<SeatType, BigDecimal> result = new EnumMap<>(SeatType.class);
        required.forEach(type -> result.put(type, given.get(type)));
        return result;
    }

    private static Set<SeatType> seatTypes(List<Seat> seats) {
        return seats.stream().map(Seat::getSeatType).collect(Collectors.toCollection(() -> EnumSet.noneOf(SeatType.class)));
    }

    private Map<SeatType, BigDecimal> pricesOf(Long showId) {
        Map<SeatType, BigDecimal> prices = new EnumMap<>(SeatType.class);
        showPriceRepository.findByShowId(showId).forEach(p -> prices.put(p.getSeatType(), p.getPrice()));
        return prices;
    }

    /** Maps shows to responses with two batched queries (prices, availability) instead of N+1. */
    private List<ShowResponse> toResponses(List<Show> shows) {
        if (shows.isEmpty()) {
            return List.of();
        }
        List<Long> ids = shows.stream().map(Show::getId).toList();
        Map<Long, Map<SeatType, BigDecimal>> prices = new HashMap<>();
        showPriceRepository.findByShowIdIn(ids).forEach(p -> prices
                .computeIfAbsent(p.getShow().getId(), k -> new EnumMap<>(SeatType.class))
                .put(p.getSeatType(), p.getPrice()));
        Map<Long, Long> available = showSeatRepository.countByShowAndStatus(ids, ShowSeatStatus.AVAILABLE).stream()
                .collect(Collectors.toMap(r -> (Long) r[0], r -> (Long) r[1]));

        return shows.stream().map(s -> {
            Movie m = s.getMovie();
            Theater t = s.getScreen().getTheater();
            return new ShowResponse(s.getId(),
                    new ShowResponse.MovieSummary(m.getId(), m.getTitle(), m.getLanguage(), m.getDurationMinutes(),
                            m.getCertificate()),
                    new ShowResponse.TheaterSummary(t.getId(), t.getName(), t.getAddress(), t.getCity().getId(),
                            t.getCity().getName()),
                    new ShowResponse.ScreenSummary(s.getScreen().getId(), s.getScreen().getName()),
                    toZoned(s.getStartTime()), toZoned(s.getEndTime()), s.getStatus(),
                    prices.getOrDefault(s.getId(), Map.of()), available.getOrDefault(s.getId(), 0L));
        }).toList();
    }

    private OffsetDateTime toZoned(Instant instant) {
        return instant.atZone(businessZone).toOffsetDateTime();
    }
}
