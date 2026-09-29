package com.sufiyan.moviebooking.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class MutableClockConfig {

    @Bean
    @Primary
    MutableClock mutableClock() {
        return new MutableClock();
    }
}
