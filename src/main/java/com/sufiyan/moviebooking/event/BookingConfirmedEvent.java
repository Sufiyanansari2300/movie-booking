package com.sufiyan.moviebooking.event;

/** Published inside the confirming transaction; listeners that react after commit get only committed bookings. */
public record BookingConfirmedEvent(Long bookingId) {
}
