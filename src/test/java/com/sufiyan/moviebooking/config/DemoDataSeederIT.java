package com.sufiyan.moviebooking.config;

import com.sufiyan.moviebooking.repository.CityRepository;
import com.sufiyan.moviebooking.repository.MovieRepository;
import com.sufiyan.moviebooking.repository.ScreenRepository;
import com.sufiyan.moviebooking.repository.SeatRepository;
import com.sufiyan.moviebooking.repository.ShowRepository;
import com.sufiyan.moviebooking.repository.ShowSeatRepository;
import com.sufiyan.moviebooking.repository.TheaterRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/** Runs against its own in-memory database so the committed demo data cannot leak into other tests. */
@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:demo_seed;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
@ActiveProfiles("demo")
class DemoDataSeederIT {

    @Autowired
    private DemoDataSeeder seeder;

    @Autowired
    private CityRepository cityRepository;

    @Autowired
    private TheaterRepository theaterRepository;

    @Autowired
    private ScreenRepository screenRepository;

    @Autowired
    private SeatRepository seatRepository;

    @Autowired
    private MovieRepository movieRepository;

    @Autowired
    private ShowRepository showRepository;

    @Autowired
    private ShowSeatRepository showSeatRepository;

    @Test
    void loadsCatalogueOnStartup_andIsIdempotent() {
        assertThat(cityRepository.count()).isEqualTo(3);
        assertThat(theaterRepository.count()).isEqualTo(4);
        assertThat(screenRepository.count()).isEqualTo(8);
        assertThat(seatRepository.count()).isEqualTo(4 * (116 + 68));
        assertThat(movieRepository.count()).isEqualTo(5);
        // 8 screens x 3 days x 2 shows; each show copies its screen's seats
        assertThat(showRepository.count()).isEqualTo(48);
        assertThat(showSeatRepository.count()).isEqualTo(3 * 2 * 4 * (116 + 68));

        seeder.run(new DefaultApplicationArguments());

        assertThat(cityRepository.count()).isEqualTo(3);
        assertThat(movieRepository.count()).isEqualTo(5);
        assertThat(showRepository.count()).isEqualTo(48);
    }
}
