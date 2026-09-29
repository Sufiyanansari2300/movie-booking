package com.sufiyan.moviebooking.controller;

import com.sufiyan.moviebooking.config.OpenApiConfig;
import com.sufiyan.moviebooking.dto.PricingRuleRequest;
import com.sufiyan.moviebooking.dto.PricingRuleResponse;
import com.sufiyan.moviebooking.service.PricingRuleService;
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

@Tag(name = "Admin - Pricing Rules", description = "Weekend / prime-time surcharges on base prices (ADMIN)")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@RestController
@RequestMapping("/api/admin/pricing-rules")
@RequiredArgsConstructor
public class AdminPricingRuleController {

    private final PricingRuleService pricingRuleService;

    @Operation(summary = "List all pricing rules")
    @GetMapping
    public List<PricingRuleResponse> list() {
        return pricingRuleService.list();
    }

    @Operation(summary = "Create a pricing rule (WEEKEND, or PRIME_TIME with a time window); applicable rules add up")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PricingRuleResponse create(@Valid @RequestBody PricingRuleRequest request) {
        return pricingRuleService.create(request);
    }

    @Operation(summary = "Update a pricing rule (affects new holds only)")
    @PutMapping("/{id}")
    public PricingRuleResponse update(@PathVariable Long id, @Valid @RequestBody PricingRuleRequest request) {
        return pricingRuleService.update(id, request);
    }

    @Operation(summary = "Delete a pricing rule")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        pricingRuleService.delete(id);
    }
}
