package com.sufiyan.moviebooking.controller;

import com.sufiyan.moviebooking.dto.ScreenRequest;
import com.sufiyan.moviebooking.dto.ScreenResponse;
import com.sufiyan.moviebooking.dto.SeatLayoutRequest;
import com.sufiyan.moviebooking.dto.SeatLayoutResponse;
import com.sufiyan.moviebooking.service.ScreenService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminScreenController {

    private final ScreenService screenService;

    @PostMapping("/theaters/{theaterId}/screens")
    @ResponseStatus(HttpStatus.CREATED)
    public ScreenResponse create(@PathVariable Long theaterId, @Valid @RequestBody ScreenRequest request) {
        return screenService.create(theaterId, request);
    }

    @PutMapping("/screens/{id}")
    public ScreenResponse rename(@PathVariable Long id, @Valid @RequestBody ScreenRequest request) {
        return screenService.rename(id, request);
    }

    @DeleteMapping("/screens/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        screenService.delete(id);
    }

    @PutMapping("/screens/{id}/layout")
    public SeatLayoutResponse replaceLayout(@PathVariable Long id, @Valid @RequestBody SeatLayoutRequest request) {
        return screenService.replaceLayout(id, request);
    }

    @GetMapping("/screens/{id}/layout")
    public SeatLayoutResponse getLayout(@PathVariable Long id) {
        return screenService.getLayout(id);
    }
}
