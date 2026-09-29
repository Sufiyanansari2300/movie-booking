package com.sufiyan.moviebooking.controller;

import com.sufiyan.moviebooking.config.OpenApiConfig;
import com.sufiyan.moviebooking.dto.RefundPolicyRequest;
import com.sufiyan.moviebooking.dto.RefundPolicyResponse;
import com.sufiyan.moviebooking.service.RefundPolicyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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

import java.util.List;

@Tag(name = "Admin - Refund Policies", description = "Time-band refund policies; one active at a time (ADMIN)")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@RestController
@RequestMapping("/api/admin/refund-policies")
@RequiredArgsConstructor
public class AdminRefundPolicyController {

    private final RefundPolicyService refundPolicyService;

    @Operation(summary = "List refund policies with their bands")
    @GetMapping
    public List<RefundPolicyResponse> list() {
        return refundPolicyService.list();
    }

    @Operation(summary = "Get a refund policy")
    @GetMapping("/{id}")
    public RefundPolicyResponse get(@PathVariable Long id) {
        return refundPolicyService.get(id);
    }

    @Operation(summary = "Create a refund policy, e.g. 48h -> 100%, 24h -> 50% (active=true makes it the active one)")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RefundPolicyResponse create(@Valid @RequestBody RefundPolicyRequest request) {
        return refundPolicyService.create(request);
    }

    @Operation(summary = "Update a refund policy that no booking uses yet")
    @PutMapping("/{id}")
    public RefundPolicyResponse update(@PathVariable Long id, @Valid @RequestBody RefundPolicyRequest request) {
        return refundPolicyService.update(id, request);
    }

    @Operation(summary = "Make this the active refund policy for new bookings")
    @PostMapping("/{id}/activate")
    public RefundPolicyResponse activate(@PathVariable Long id) {
        return refundPolicyService.activate(id);
    }

    @Operation(summary = "Delete an inactive refund policy that no booking uses")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        refundPolicyService.delete(id);
    }
}
