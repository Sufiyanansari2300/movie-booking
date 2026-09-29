package com.sufiyan.moviebooking.notification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Mock provider: "sends" by logging. A real SMTP/SES/SMS sender would be another implementation. */
@Slf4j
@Component
public class LoggingEmailSender implements NotificationSender {

    @Override
    public void send(OutgoingMessage message) {
        log.info("[{}] to={} subject=\"{}\"\n{}", message.channel(), message.recipient(), message.subject(),
                message.body());
    }
}
