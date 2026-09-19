package com.aman.firstclubmembership2.domain;

import com.aman.firstclubmembership2.enums.BillingCycle;

import java.math.BigDecimal;

public class MembershipPlan {

    private final String planId;
    private final String name;
    private final BillingCycle billingCycle;
    private final BigDecimal price;

    public MembershipPlan(String planId, String name, BillingCycle billingCycle, BigDecimal price) {
        this.planId = planId;
        this.name = name;
        this.billingCycle = billingCycle;
        this.price = price;
    }

    public String getPlanId() {
        return planId;
    }

    public String getName() {
        return name;
    }

    public BillingCycle getBillingCycle() {
        return billingCycle;
    }

    public BigDecimal getPrice() {
        return price;
    }
}
