package com.sufiyan.moviebooking.dto;

import com.sufiyan.moviebooking.entity.NotificationChannel;
import com.sufiyan.moviebooking.entity.NotificationStatus;
import com.sufiyan.moviebooking.entity.NotificationType;

import java.time.OffsetDateTime;

public record NotificationResponse(Long id, Long bookingId, NotificationType type, NotificationChannel channel,
                                   String recipient, String subject, String body, NotificationStatus status,
                                   int attempts, String lastError, OffsetDateTime sentAt, OffsetDateTime createdAt) {
}
