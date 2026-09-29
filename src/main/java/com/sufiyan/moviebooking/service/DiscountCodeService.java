package com.sufiyan.moviebooking.service;

import com.sufiyan.moviebooking.dto.DiscountCodeRequest;
import com.sufiyan.moviebooking.dto.DiscountCodeResponse;
import com.sufiyan.moviebooking.entity.DiscountCode;
import com.sufiyan.moviebooking.entity.DiscountType;
import com.sufiyan.moviebooking.exception.BadRequestException;
import com.sufiyan.moviebooking.exception.ConflictException;
import com.sufiyan.moviebooking.exception.ResourceNotFoundException;
import com.sufiyan.moviebooking.repository.BookingRepository;
import com.sufiyan.moviebooking.repository.DiscountCodeRepository;
import com.sufiyan.moviebooking.repository.DiscountRedemptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class DiscountCodeService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final DiscountCodeRepository repository;
    private final DiscountRedemptionRepository redemptionRepository;
    private final BookingRepository bookingRepository;
    private final ZoneId businessZone;

    public static String normalize(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
    }

    @Transactional
    public DiscountCodeResponse create(DiscountCodeRequest request) {
        validate(request);
        String code = normalize(request.code());
        if (repository.existsByCode(code)) {
            throw new ConflictException("DISCOUNT_ALREADY_EXISTS", "Discount code already exists: " + code);
        }
        DiscountCode entity = new DiscountCode(code, request.discountType(), request.discountValue());
        apply(entity, request);
        return toResponse(repository.save(entity));
    }

    /** The code string itself cannot change; everything else can. */
    @Transactional
    public DiscountCodeResponse update(Long id, DiscountCodeRequest request) {
        validate(request);
        DiscountCode entity = getEntity(id);
        if (!entity.getCode().equals(normalize(request.code()))) {
            throw new BadRequestException("INVALID_DISCOUNT", "The code of an existing discount cannot be changed");
        }
        if (request.usageLimit() != null && request.usageLimit() < entity.getUsedCount()) {
            throw new BadRequestException("INVALID_DISCOUNT",
                    "usageLimit cannot be below the " + entity.getUsedCount() + " uses so far");
        }
        entity.setDiscountType(request.discountType());
        entity.setDiscountValue(request.discountValue());
        apply(entity, request);
        return toResponse(entity);
    }

    /** Only never-used codes can be deleted; deactivate a used one instead (keeps booking history intact). */
    @Transactional
    public void delete(Long id) {
        DiscountCode entity = getEntity(id);
        if (redemptionRepository.existsByDiscountCodeId(id) || bookingRepository.existsByDiscountCodeId(id)) {
            throw new ConflictException("DISCOUNT_IN_USE", "The code has been used; set active=false instead");
        }
        repository.delete(entity);
    }

    @Transactional(readOnly = true)
    public List<DiscountCodeResponse> list() {
        return repository.findAllByOrderByCodeAsc().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public DiscountCodeResponse get(Long id) {
        return toResponse(getEntity(id));
    }

    private DiscountCode getEntity(Long id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Discount code", id));
    }

    private static void apply(DiscountCode entity, DiscountCodeRequest r) {
        entity.setDescription(r.description());
        entity.setMaxDiscountAmount(r.maxDiscountAmount());
        entity.setMinOrderAmount(r.minOrderAmount());
        entity.setValidFrom(toInstant(r.validFrom()));
        entity.setValidUntil(toInstant(r.validUntil()));
        entity.setUsageLimit(r.usageLimit());
        entity.setPerUserLimit(r.perUserLimit());
        entity.setActive(r.active() == null || r.active());
    }

    private static void validate(DiscountCodeRequest r) {
        if (r.discountType() == DiscountType.PERCENT && r.discountValue().compareTo(HUNDRED) > 0) {
            throw new BadRequestException("INVALID_DISCOUNT", "A percent discount cannot exceed 100");
        }
        if (r.discountType() == DiscountType.FLAT && r.maxDiscountAmount() != null) {
            throw new BadRequestException("INVALID_DISCOUNT", "maxDiscountAmount only applies to PERCENT discounts");
        }
        if (r.validFrom() != null && r.validUntil() != null && !r.validFrom().isBefore(r.validUntil())) {
            throw new BadRequestException("INVALID_DISCOUNT", "validFrom must be before validUntil");
        }
    }

    private static Instant toInstant(OffsetDateTime time) {
        return time == null ? null : time.toInstant();
    }

    private DiscountCodeResponse toResponse(DiscountCode d) {
        return new DiscountCodeResponse(d.getId(), d.getCode(), d.getDescription(), d.getDiscountType(),
                d.getDiscountValue(), d.getMaxDiscountAmount(), d.getMinOrderAmount(),
                d.getValidFrom() == null ? null : d.getValidFrom().atZone(businessZone).toOffsetDateTime(),
                d.getValidUntil() == null ? null : d.getValidUntil().atZone(businessZone).toOffsetDateTime(),
                d.getUsageLimit(), d.getPerUserLimit(), d.getUsedCount(), d.isActive());
    }
}
