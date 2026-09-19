package com.aman.firstclubmembership2.rule;

import com.aman.firstclubmembership2.model.UserMetrics;

/** Requires a minimum number of monthly orders. */
public class OrderCountRule implements TierQualificationRule {

    private final int minOrders;

    public OrderCountRule(int minOrders) {
        this.minOrders = minOrders;
    }

    @Override
    public boolean isEligible(UserMetrics metrics) {
        return metrics != null && metrics.monthlyOrderCount() >= minOrders;
    }
}
