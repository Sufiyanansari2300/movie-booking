package com.sufiyan.moviebooking.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * Time setup. Instants are stored in UTC; {@code app.timezone} is the business time zone used for calendar logic
 * (show dates, weekends). A single injectable clock keeps time-dependent logic testable.
 */
@Configuration
@EnableConfigurationProperties({ShowProperties.class, BookingProperties.class, PaymentProperties.class})
public class TimeConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    ZoneId businessZone(@Value("${app.timezone}") String zone) {
        return ZoneId.of(zone);
    }
}
