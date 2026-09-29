package com.sufiyan.moviebooking.support;

import com.sufiyan.moviebooking.dto.CreateShowRequest;
import com.sufiyan.moviebooking.dto.SeatLayoutRequest;
import com.sufiyan.moviebooking.dto.SeatLayoutRequest.Section;
import com.sufiyan.moviebooking.entity.City;
import com.sufiyan.moviebooking.entity.Movie;
import com.sufiyan.moviebooking.entity.Role;
import com.sufiyan.moviebooking.entity.Screen;
import com.sufiyan.moviebooking.entity.SeatType;
import com.sufiyan.moviebooking.entity.ShowSeat;
import com.sufiyan.moviebooking.entity.Theater;
import com.sufiyan.moviebooking.entity.User;
import com.sufiyan.moviebooking.repository.CityRepository;
import com.sufiyan.moviebooking.repository.MovieRepository;
import com.sufiyan.moviebooking.repository.ScreenRepository;
import com.sufiyan.moviebooking.repository.ShowSeatRepository;
import com.sufiyan.moviebooking.repository.TheaterRepository;
import com.sufiyan.moviebooking.service.ScreenService;
import com.sufiyan.moviebooking.service.ShowService;
import com.sufiyan.moviebooking.service.UserService;
import org.springframework.context.ApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Builds catalogue data for tests through the real services. Names are made unique so tests that commit data
 * (concurrency tests) can run repeatedly against the same database.
 */
public class TestData {

    public static final String PASSWORD = "password123";
    public static final BigDecimal REGULAR_PRICE = new BigDecimal("200.00");
    public static final BigDecimal PREMIUM_PRICE = new BigDecimal("350.00");

    private final ApplicationContext ctx;

    public TestData(ApplicationContext ctx) {
        this.ctx = ctx;
    }

    public record ShowFixture(long showId, List<ShowSeat> seats) {

        /** Show seat id by label, e.g. "A1". */
        public long seat(String label) {
            return seats.stream().filter(s -> s.getSeat().getLabel().equals(label)).findFirst()
                    .orElseThrow().getId();
        }
    }

    public User user(Role role) {
        String email = role.name().toLowerCase() + "-" + UUID.randomUUID() + "@example.com";
        return ctx.getBean(UserService.class).createUser("Test " + role, email, PASSWORD, role);
    }

    /** A show tomorrow evening on a fresh 3-row screen: rows A-B REGULAR x 5, row C PREMIUM x 5. */
    public ShowFixture show() {
        return show(LocalDate.now(ZoneId.of("Asia/Kolkata")).plusDays(1).atTime(18, 0)
                .atZone(ZoneId.of("Asia/Kolkata")).toOffsetDateTime());
    }

    public ShowFixture show(OffsetDateTime start) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        City city = ctx.getBean(CityRepository.class).save(new City("City " + suffix, "State"));
        Theater theater = ctx.getBean(TheaterRepository.class).save(new Theater(city, "Theater " + suffix, "Addr"));
        Screen screen = ctx.getBean(ScreenRepository.class).save(new Screen(theater, "Audi 1"));
        ctx.getBean(ScreenService.class).replaceLayout(screen.getId(), new SeatLayoutRequest(List.of(
                new Section("A-B", 5, SeatType.REGULAR), new Section("C", 5, SeatType.PREMIUM))));
        Movie movie = ctx.getBean(MovieRepository.class)
                .save(new Movie("Movie " + suffix, null, "English", "Drama", 120, "UA", null));
        long showId = ctx.getBean(ShowService.class).create(new CreateShowRequest(movie.getId(), screen.getId(), start,
                Map.of(SeatType.REGULAR, REGULAR_PRICE, SeatType.PREMIUM, PREMIUM_PRICE))).id();
        return new ShowFixture(showId, ctx.getBean(ShowSeatRepository.class).findSeatMap(showId));
    }
}
