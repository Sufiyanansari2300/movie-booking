package com.sufiyan.moviebooking.event;

/** A confirmed booking was cancelled (by the customer or because its show was cancelled). */
public record BookingCancelledEvent(Long bookingId) {
}
