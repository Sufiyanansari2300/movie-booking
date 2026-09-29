package com.sufiyan.moviebooking.notification;

import com.sufiyan.moviebooking.entity.NotificationChannel;

/** Port to an email/SMS provider. Throw on failure; the caller records it and retries later. */
public interface NotificationSender {

    void send(OutgoingMessage message);

    record OutgoingMessage(NotificationChannel channel, String recipient, String subject, String body) {
    }
}
