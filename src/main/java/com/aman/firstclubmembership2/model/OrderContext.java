package com.aman.firstclubmembership2.model;

import java.math.BigDecimal;

/**
 * Minimal, evaluation-time snapshot of an order used to apply tier benefits against.
 * Not a persisted domain object - just the inputs a {@code MembershipBenefit} needs.
 */
public record OrderContext(
        BigDecimal subtotal,
        String category,
        BigDecimal standardDeliveryFee,
        boolean isExclusiveDeal,
        boolean isPrioritySupportRequested
) {
}
