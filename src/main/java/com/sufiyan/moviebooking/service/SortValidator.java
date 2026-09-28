package com.sufiyan.moviebooking.service;

import com.sufiyan.moviebooking.exception.BadRequestException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Set;

/** Restricts client-supplied sort fields to an explicit allow-list per endpoint. */
public final class SortValidator {

    private SortValidator() {
    }

    public static Pageable requireAllowed(Pageable pageable, Set<String> allowed) {
        for (Sort.Order order : pageable.getSort()) {
            if (!allowed.contains(order.getProperty())) {
                throw new BadRequestException("INVALID_PARAMETER",
                        "Cannot sort by '" + order.getProperty() + "'. Allowed: " + String.join(", ", allowed));
            }
        }
        return pageable;
    }
}
