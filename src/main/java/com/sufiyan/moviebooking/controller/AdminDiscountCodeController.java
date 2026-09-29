package com.sufiyan.moviebooking.controller;

import com.sufiyan.moviebooking.config.OpenApiConfig;
import com.sufiyan.moviebooking.dto.DiscountCodeRequest;
import com.sufiyan.moviebooking.dto.DiscountCodeResponse;
import com.sufiyan.moviebooking.service.DiscountCodeService;
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

@Tag(name = "Admin - Discount Codes", description = "Percent / flat discount codes with limits (ADMIN)")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@RestController
@RequestMapping("/api/admin/discount-codes")
@RequiredArgsConstructor
public class AdminDiscountCodeController {

    private final DiscountCodeService discountCodeService;

    @Operation(summary = "List all discount codes with usage counts")
    @GetMapping
    public List<DiscountCodeResponse> list() {
        return discountCodeService.list();
    }

    @Operation(summary = "Get a discount code")
    @GetMapping("/{id}")
    public DiscountCodeResponse get(@PathVariable Long id) {
        return discountCodeService.get(id);
    }

    @Operation(summary = "Create a discount code (PERCENT with optional cap, or FLAT; optional validity, limits, minimum order)")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DiscountCodeResponse create(@Valid @RequestBody DiscountCodeRequest request) {
        return discountCodeService.create(request);
    }

    @Operation(summary = "Update a discount code (the code text itself is fixed); set active=false to disable")
    @PutMapping("/{id}")
    public DiscountCodeResponse update(@PathVariable Long id, @Valid @RequestBody DiscountCodeRequest request) {
        return discountCodeService.update(id, request);
    }

    @Operation(summary = "Delete a discount code that has never been used")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        discountCodeService.delete(id);
    }
}
