package com.sufiyan.moviebooking.concurrency;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * The same concurrency scenarios against a real MySQL, whose InnoDB row locking is what production uses.
 * Opt-in: set MYSQL_IT_URL (e.g. jdbc:mysql://localhost:3306/movie_booking_it?createDatabaseIfNotExist=true),
 * MYSQL_IT_USERNAME and MYSQL_IT_PASSWORD. Use a dedicated schema: the test commits data.
 */
@SpringBootTest(properties = "spring.datasource.hikari.maximum-pool-size=32")
@EnabledIfEnvironmentVariable(named = "MYSQL_IT_URL", matches = ".+")
class BookingConcurrencyMySqlIT extends BookingConcurrencyScenarios {

    @DynamicPropertySource
    static void mysql(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("MYSQL_IT_URL"));
        registry.add("spring.datasource.username", () -> System.getenv("MYSQL_IT_USERNAME"));
        registry.add("spring.datasource.password", () -> System.getenv().getOrDefault("MYSQL_IT_PASSWORD", ""));
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
    }
}
