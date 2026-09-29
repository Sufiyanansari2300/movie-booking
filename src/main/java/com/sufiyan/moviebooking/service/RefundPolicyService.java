package com.sufiyan.moviebooking.service;

import com.sufiyan.moviebooking.dto.RefundPolicyRequest;
import com.sufiyan.moviebooking.dto.RefundPolicyResponse;
import com.sufiyan.moviebooking.entity.RefundPolicy;
import com.sufiyan.moviebooking.entity.RefundPolicyRule;
import com.sufiyan.moviebooking.exception.BadRequestException;
import com.sufiyan.moviebooking.exception.ConflictException;
import com.sufiyan.moviebooking.exception.ResourceNotFoundException;
import com.sufiyan.moviebooking.repository.BookingRepository;
import com.sufiyan.moviebooking.repository.RefundPolicyRepository;
import com.sufiyan.moviebooking.repository.RefundPolicyRuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Admin management of refund policies. Exactly one policy is active; bookings keep the policy that was active
 * when they were paid, so a policy used by bookings is frozen (create and activate a new one instead).
 */
@Service
@RequiredArgsConstructor
public class RefundPolicyService {

    private final RefundPolicyRepository policyRepository;
    private final RefundPolicyRuleRepository ruleRepository;
    private final BookingRepository bookingRepository;

    @Transactional
    public RefundPolicyResponse create(RefundPolicyRequest request) {
        validate(request);
        String name = request.name().trim();
        if (policyRepository.existsByNameIgnoreCase(name)) {
            throw new ConflictException("REFUND_POLICY_ALREADY_EXISTS", "Refund policy already exists: " + name);
        }
        RefundPolicy policy = policyRepository.save(new RefundPolicy(name));
        saveRules(policy, request.rules());
        if (Boolean.TRUE.equals(request.active())) {
            makeActive(policy);
        }
        return get(policy.getId());
    }

    @Transactional
    public RefundPolicyResponse update(Long id, RefundPolicyRequest request) {
        validate(request);
        RefundPolicy policy = getEntity(id);
        requireUnused(id);
        String name = request.name().trim();
        if (policyRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw new ConflictException("REFUND_POLICY_ALREADY_EXISTS", "Refund policy already exists: " + name);
        }
        policy.setName(name);
        ruleRepository.deleteByPolicyId(id);
        RefundPolicy fresh = getEntity(id); // the rule delete cleared the persistence context
        saveRules(fresh, request.rules());
        if (Boolean.TRUE.equals(request.active())) {
            makeActive(fresh);
        }
        return get(id);
    }

    @Transactional
    public RefundPolicyResponse activate(Long id) {
        makeActive(getEntity(id));
        return get(id);
    }

    @Transactional
    public void delete(Long id) {
        RefundPolicy policy = getEntity(id);
        requireUnused(id);
        if (policy.isActive()) {
            throw new ConflictException("REFUND_POLICY_ACTIVE", "Activate another policy before deleting this one");
        }
        ruleRepository.deleteByPolicyId(id);
        policyRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public List<RefundPolicyResponse> list() {
        return policyRepository.findAllByOrderByNameAsc().stream().map(p -> get(p.getId())).toList();
    }

    @Transactional(readOnly = true)
    public RefundPolicyResponse get(Long id) {
        RefundPolicy policy = getEntity(id);
        List<RefundPolicyResponse.Band> bands = ruleRepository.findByPolicyIdOrderByMinHoursBeforeShowDesc(id).stream()
                .map(r -> new RefundPolicyResponse.Band(r.getMinHoursBeforeShow(), r.getRefundPercent())).toList();
        return new RefundPolicyResponse(policy.getId(), policy.getName(), policy.isActive(),
                bookingRepository.existsByRefundPolicyId(id), bands);
    }

    /** Entity updates (a handful of rows) so already-loaded policies never keep a stale active flag. */
    private void makeActive(RefundPolicy policy) {
        policyRepository.findByActiveTrue().stream()
                .filter(p -> !p.getId().equals(policy.getId()))
                .forEach(p -> p.setActive(false));
        policy.setActive(true);
    }

    private void saveRules(RefundPolicy policy, List<RefundPolicyRequest.Band> bands) {
        bands.forEach(b -> ruleRepository.save(new RefundPolicyRule(policy, b.minHoursBeforeShow(), b.refundPercent())));
    }

    private void requireUnused(Long id) {
        if (bookingRepository.existsByRefundPolicyId(id)) {
            throw new ConflictException("REFUND_POLICY_IN_USE",
                    "Bookings use this policy, so it cannot change; create and activate a new policy instead");
        }
    }

    private RefundPolicy getEntity(Long id) {
        return policyRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Refund policy", id));
    }

    /** Thresholds must be unique, and cancelling earlier must never refund less than cancelling later. */
    private static void validate(RefundPolicyRequest request) {
        Set<Integer> seen = new HashSet<>();
        for (RefundPolicyRequest.Band band : request.rules()) {
            if (!seen.add(band.minHoursBeforeShow())) {
                throw new BadRequestException("INVALID_REFUND_POLICY",
                        "Duplicate band for " + band.minHoursBeforeShow() + " hours");
            }
        }
        List<RefundPolicyRequest.Band> sorted = request.rules().stream()
                .sorted(Comparator.comparingInt(RefundPolicyRequest.Band::minHoursBeforeShow).reversed()).toList();
        for (int i = 1; i < sorted.size(); i++) {
            if (sorted.get(i).refundPercent().compareTo(sorted.get(i - 1).refundPercent()) > 0) {
                throw new BadRequestException("INVALID_REFUND_POLICY", "Cancelling earlier must refund at least as "
                        + "much: " + sorted.get(i).minHoursBeforeShow() + "h refunds more than "
                        + sorted.get(i - 1).minHoursBeforeShow() + "h");
            }
        }
    }
}
