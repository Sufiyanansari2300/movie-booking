package com.sufiyan.moviebooking.config;

import com.sufiyan.moviebooking.dto.CityRequest;
import com.sufiyan.moviebooking.dto.CreateShowRequest;
import com.sufiyan.moviebooking.dto.DiscountCodeRequest;
import com.sufiyan.moviebooking.dto.PricingRuleRequest;
import com.sufiyan.moviebooking.dto.MovieRequest;
import com.sufiyan.moviebooking.dto.ScreenRequest;
import com.sufiyan.moviebooking.dto.SeatLayoutRequest;
import com.sufiyan.moviebooking.dto.SeatLayoutRequest.Section;
import com.sufiyan.moviebooking.dto.TheaterRequest;
import com.sufiyan.moviebooking.entity.DiscountType;
import com.sufiyan.moviebooking.entity.PricingRuleType;
import com.sufiyan.moviebooking.entity.SeatType;
import com.sufiyan.moviebooking.repository.CityRepository;
import com.sufiyan.moviebooking.service.CityService;
import com.sufiyan.moviebooking.service.DiscountCodeService;
import com.sufiyan.moviebooking.service.MovieService;
import com.sufiyan.moviebooking.service.PricingRuleService;
import com.sufiyan.moviebooking.service.ScreenService;
import com.sufiyan.moviebooking.service.ShowService;
import com.sufiyan.moviebooking.service.TheaterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Loads sample catalogue data (upcoming shows, pricing rules, discount codes) for demos. Only active with the {@code demo} profile and only when the
 * database has no cities yet, so it never touches real data and is safe to restart.
 * Goes through the services so the same validation rules apply as for the admin API.
 */
@Slf4j
@Component
@Profile("demo")
@Order(10) // after AdminBootstrap
@RequiredArgsConstructor
public class DemoDataSeeder implements ApplicationRunner {

    private static final SeatLayoutRequest LARGE_SCREEN = new SeatLayoutRequest(List.of(
            new Section("A-H", 12, SeatType.REGULAR),
            new Section("I-J", 10, SeatType.PREMIUM)));

    private static final SeatLayoutRequest SMALL_SCREEN = new SeatLayoutRequest(List.of(
            new Section("A-F", 10, SeatType.REGULAR),
            new Section("G", 8, SeatType.PREMIUM)));

    private final CityRepository cityRepository;
    private final CityService cityService;
    private final TheaterService theaterService;
    private final ScreenService screenService;
    private final MovieService movieService;
    private final ShowService showService;
    private final PricingRuleService pricingRuleService;
    private final DiscountCodeService discountCodeService;
    private final Clock clock;
    private final ZoneId businessZone;

    private final List<Long> largeScreens = new ArrayList<>();
    private final List<Long> smallScreens = new ArrayList<>();
    private final List<Long> movies = new ArrayList<>();

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (cityRepository.count() > 0) {
            log.info("Demo data skipped: catalogue is not empty");
            return;
        }
        long mumbai = cityService.create(new CityRequest("Mumbai", "Maharashtra")).id();
        long bengaluru = cityService.create(new CityRequest("Bengaluru", "Karnataka")).id();
        long delhi = cityService.create(new CityRequest("Delhi", "Delhi")).id();

        theater(mumbai, "Galaxy Cinemas", "Lower Parel");
        theater(mumbai, "Starlight Multiplex", "Andheri West");
        theater(bengaluru, "Orbit Cinemas", "Koramangala");
        theater(delhi, "Metro Screens", "Saket");

        movie("Inception", "A thief who steals secrets through dreams.", "English", "Sci-Fi", 148, "UA", "2010-07-16");
        movie("Interstellar", "Explorers travel through a wormhole to save humanity.", "English", "Sci-Fi", 169, "UA", "2014-11-07");
        movie("The Dark Knight", "Batman faces the Joker.", "English", "Action", 152, "UA", "2008-07-18");
        movie("3 Idiots", "Two friends search for their long-lost companion.", "Hindi", "Comedy", 170, "U", "2009-12-25");
        movie("Jawan", "A man sets out to rectify wrongs in society.", "Hindi", "Action", 169, "UA", "2023-09-07");

        int shows = scheduleShows();

        pricingRuleService.create(new PricingRuleRequest("Weekend surcharge", PricingRuleType.WEEKEND,
                new BigDecimal("20"), null, null, true));
        pricingRuleService.create(new PricingRuleRequest("Prime time", PricingRuleType.PRIME_TIME,
                new BigDecimal("10"), LocalTime.of(18, 0), LocalTime.of(22, 0), true));
        discountCodeService.create(new DiscountCodeRequest("WELCOME10", "10% off your first booking, up to 100",
                DiscountType.PERCENT, new BigDecimal("10"), new BigDecimal("100"), null, null, null, null, 1, true));
        discountCodeService.create(new DiscountCodeRequest("FLAT50", "50 off orders of 300 or more",
                DiscountType.FLAT, new BigDecimal("50"), null, new BigDecimal("300"), null, null, 1000, null, true));

        log.info("Demo data loaded: 3 cities, 4 theaters, 8 screens, 5 movies, {} shows, 2 pricing rules, "
                + "2 discount codes", shows);
    }

    private void theater(long cityId, String name, String address) {
        long theaterId = theaterService.create(new TheaterRequest(cityId, name, address)).id();
        long audi1 = screenService.create(theaterId, new ScreenRequest("Audi 1")).id();
        long audi2 = screenService.create(theaterId, new ScreenRequest("Audi 2")).id();
        screenService.replaceLayout(audi1, LARGE_SCREEN);
        screenService.replaceLayout(audi2, SMALL_SCREEN);
        largeScreens.add(audi1);
        smallScreens.add(audi2);
    }

    /** Two shows a day (13:00, 19:00) on every screen for the next 3 days, rotating movies across screens. */
    private int scheduleShows() {
        Map<SeatType, BigDecimal> largePrices = Map.of(SeatType.REGULAR, new BigDecimal("200.00"),
                SeatType.PREMIUM, new BigDecimal("350.00"));
        Map<SeatType, BigDecimal> smallPrices = Map.of(SeatType.REGULAR, new BigDecimal("180.00"),
                SeatType.PREMIUM, new BigDecimal("300.00"));
        LocalDate today = LocalDate.now(clock.withZone(businessZone));
        int count = 0;
        for (int day = 1; day <= 3; day++) {
            for (LocalTime time : List.of(LocalTime.of(13, 0), LocalTime.of(19, 0))) {
                var start = today.plusDays(day).atTime(time).atZone(businessZone).toOffsetDateTime();
                for (int i = 0; i < largeScreens.size(); i++) {
                    Long movie = movies.get((i + day + time.getHour()) % movies.size());
                    showService.create(new CreateShowRequest(movie, largeScreens.get(i), start, largePrices));
                    Long otherMovie = movies.get((i + day + time.getHour() + 1) % movies.size());
                    showService.create(new CreateShowRequest(otherMovie, smallScreens.get(i), start, smallPrices));
                    count += 2;
                }
            }
        }
        return count;
    }

    private void movie(String title, String description, String language, String genre, int minutes,
                       String certificate, String releaseDate) {
        movies.add(movieService.create(new MovieRequest(title, description, language, genre, minutes, certificate,
                LocalDate.parse(releaseDate))).id());
    }
}
