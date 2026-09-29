package com.sufiyan.moviebooking.support;

import com.sufiyan.moviebooking.notification.NotificationSender;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Test double: records messages, can fail the next N sends, and can block until released. */
public class RecordingNotificationSender implements NotificationSender {

    public final List<OutgoingMessage> sent = new CopyOnWriteArrayList<>();
    public final List<String> threads = new CopyOnWriteArrayList<>();
    private final AtomicInteger failNext = new AtomicInteger();
    private volatile CountDownLatch gate;

    @Override
    public void send(OutgoingMessage message) {
        threads.add(Thread.currentThread().getName());
        CountDownLatch g = gate;
        if (g != null) {
            try {
                g.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (failNext.getAndUpdate(n -> n > 0 ? n - 1 : 0) > 0) {
            throw new IllegalStateException("provider down");
        }
        sent.add(message);
    }

    public void failNext(int times) {
        failNext.set(times);
    }

    /** Makes sends wait until {@link #open()} is called. */
    public void close() {
        gate = new CountDownLatch(1);
    }

    public void open() {
        CountDownLatch g = gate;
        gate = null;
        if (g != null) {
            g.countDown();
        }
    }

    public void reset() {
        open();
        sent.clear();
        threads.clear();
        failNext.set(0);
    }
}
