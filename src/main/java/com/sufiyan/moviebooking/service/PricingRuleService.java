package com.sufiyan.moviebooking.service;

import com.sufiyan.moviebooking.dto.PricingRuleRequest;
import com.sufiyan.moviebooking.dto.PricingRuleResponse;
import com.sufiyan.moviebooking.entity.PricingRule;
import com.sufiyan.moviebooking.entity.PricingRuleType;
import com.sufiyan.moviebooking.exception.BadRequestException;
import com.sufiyan.moviebooking.exception.ConflictException;
import com.sufiyan.moviebooking.exception.ResourceNotFoundException;
import com.sufiyan.moviebooking.repository.PricingRuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Admin management of pricing rules. Changes affect new holds only; held/confirmed bookings keep their prices. */
@Service
@RequiredArgsConstructor
public class PricingRuleService {

    private final PricingRuleRepository repository;

    @Transactional
    public PricingRuleResponse create(PricingRuleRequest request) {
        validate(request);
        String name = request.name().trim();
        if (repository.existsByNameIgnoreCase(name)) {
            throw new ConflictException("PRICING_RULE_ALREADY_EXISTS", "Pricing rule already exists: " + name);
        }
        PricingRule rule = new PricingRule(name, request.ruleType(), request.adjustmentPercent(),
                request.windowStart(), request.windowEnd(), request.active() == null || request.active());
        return PricingRuleResponse.from(repository.save(rule));
    }

    @Transactional
    public PricingRuleResponse update(Long id, PricingRuleRequest request) {
        validate(request);
        PricingRule rule = getEntity(id);
        String name = request.name().trim();
        if (repository.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw new ConflictException("PRICING_RULE_ALREADY_EXISTS", "Pricing rule already exists: " + name);
        }
        rule.setName(name);
        rule.setRuleType(request.ruleType());
        rule.setAdjustmentPercent(request.adjustmentPercent());
        rule.setWindowStart(request.windowStart());
        rule.setWindowEnd(request.windowEnd());
        rule.setActive(request.active() == null || request.active());
        return PricingRuleResponse.from(rule);
    }

    @Transactional
    public void delete(Long id) {
        repository.delete(getEntity(id));
    }

    @Transactional(readOnly = true)
    public List<PricingRuleResponse> list() {
        return repository.findAllByOrderByNameAsc().stream().map(PricingRuleResponse::from).toList();
    }

    private PricingRule getEntity(Long id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Pricing rule", id));
    }

    private static void validate(PricingRuleRequest r) {
        if (r.adjustmentPercent().signum() == 0) {
            throw new BadRequestException("INVALID_PRICING_RULE", "adjustmentPercent must not be zero");
        }
        boolean hasWindow = r.windowStart() != null || r.windowEnd() != null;
        if (r.ruleType() == PricingRuleType.PRIME_TIME) {
            if (r.windowStart() == null || r.windowEnd() == null || !r.windowStart().isBefore(r.windowEnd())) {
                throw new BadRequestException("INVALID_PRICING_RULE",
                        "PRIME_TIME needs windowStart before windowEnd (same day), e.g. 18:00-22:00");
            }
        } else if (hasWindow) {
            throw new BadRequestException("INVALID_PRICING_RULE", r.ruleType() + " rules do not take a time window");
        }
    }
}
