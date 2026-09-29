package com.sufiyan.moviebooking.concurrency;

import org.springframework.boot.test.context.SpringBootTest;

/** Concurrency scenarios on H2, in a database of their own because the data is committed. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:concurrency;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "spring.datasource.hikari.maximum-pool-size=32"
})
class BookingConcurrencyIT extends BookingConcurrencyScenarios {
}
