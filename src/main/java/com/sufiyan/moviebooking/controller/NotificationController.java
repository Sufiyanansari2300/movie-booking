package com.sufiyan.moviebooking.controller;

import com.sufiyan.moviebooking.config.OpenApiConfig;
import com.sufiyan.moviebooking.dto.NotificationResponse;
import com.sufiyan.moviebooking.dto.PageResponse;
import com.sufiyan.moviebooking.entity.NotificationStatus;
import com.sufiyan.moviebooking.notification.NotificationService;
import com.sufiyan.moviebooking.security.AppUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Notifications", description = "Confirmation, reminder and cancellation messages")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@RestController
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @Operation(summary = "Notifications sent for one of your bookings")
    @GetMapping("/api/bookings/{id}/notifications")
    public List<NotificationResponse> forBooking(@AuthenticationPrincipal AppUserPrincipal caller, @PathVariable Long id) {
        return notificationService.forBooking(caller, id);
    }

    @Operation(summary = "All notifications, optionally by status (ADMIN), newest first")
    @GetMapping("/api/admin/notifications")
    public PageResponse<NotificationResponse> search(
            @RequestParam(required = false) NotificationStatus status,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return notificationService.search(status, pageable);
    }
}
