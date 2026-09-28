package com.sufiyan.moviebooking.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Single injectable clock so time-dependent logic (token expiry, seat holds, refunds) is testable.
 */
@Configuration
public class TimeConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
