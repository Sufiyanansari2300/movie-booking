package com.sufiyan.moviebooking.repository;

import com.sufiyan.moviebooking.entity.Notification;
import com.sufiyan.moviebooking.entity.NotificationStatus;
import com.sufiyan.moviebooking.entity.NotificationType;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    boolean existsByBookingIdAndType(Long bookingId, NotificationType type);

    List<Notification> findByBookingIdOrderByCreatedAtAsc(Long bookingId);

    Page<Notification> findByStatus(NotificationStatus status, Pageable pageable);

    /** FAILED ones below the attempt limit, plus PENDING ones stuck (e.g. the app stopped mid-send). */
    @Query("""
            select n.id from Notification n
            where (n.status = com.sufiyan.moviebooking.entity.NotificationStatus.FAILED and n.attempts < :maxAttempts)
               or (n.status = com.sufiyan.moviebooking.entity.NotificationStatus.PENDING and n.createdAt < :stuckBefore)
            order by n.updatedAt
            """)
    List<Long> findRetryable(@Param("maxAttempts") int maxAttempts, @Param("stuckBefore") Instant stuckBefore,
                             Limit limit);
}
