package com.aman.firstclubmembership2.rule;

import com.aman.firstclubmembership2.model.UserMetrics;

import java.math.BigDecimal;

/** Requires a minimum monthly order value. */
public class OrderValueRule implements TierQualificationRule {

    private final BigDecimal minOrderValue;

    public OrderValueRule(BigDecimal minOrderValue) {
        this.minOrderValue = minOrderValue;
    }

    @Override
    public boolean isEligible(UserMetrics metrics) {
        return metrics != null && metrics.monthlyOrderValue() != null
                && metrics.monthlyOrderValue().compareTo(minOrderValue) >= 0;
    }
}
