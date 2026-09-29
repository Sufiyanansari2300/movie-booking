package com.sufiyan.moviebooking.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** Test clock that can be moved forward, e.g. to make seat holds expire without waiting. */
public class MutableClock extends Clock {

    private volatile Instant now = Instant.now();

    public void advance(Duration duration) {
        now = now.plus(duration);
    }

    public void reset() {
        now = Instant.now();
    }

    @Override
    public Instant instant() {
        return now;
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return Clock.fixed(now, zone);
    }
}
