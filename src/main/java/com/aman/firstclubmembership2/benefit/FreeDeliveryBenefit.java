package com.aman.firstclubmembership2.benefit;

import com.aman.firstclubmembership2.model.OrderBenefitsResult;
import com.aman.firstclubmembership2.model.OrderContext;

import java.math.BigDecimal;

/** Waives the delivery fee for orders at or above a minimum value ("eligible orders"); zero minimum means always free. */
public class FreeDeliveryBenefit implements MembershipBenefit {

    private final BigDecimal minOrderValue;

    public FreeDeliveryBenefit(BigDecimal minOrderValue) {
        this.minOrderValue = minOrderValue;
    }

    public FreeDeliveryBenefit() {
        this(BigDecimal.ZERO);
    }

    @Override
    public String getBenefitCode() {
        return "FREE_DELIVERY_ABOVE_" + minOrderValue;
    }

    @Override
    public void apply(OrderContext context, OrderBenefitsResult result) {
        if (context.subtotal().compareTo(minOrderValue) >= 0) {
            result.setDeliveryFee(BigDecimal.ZERO);
            result.addSummary("Free delivery applied (order >= " + minOrderValue + ")");
        }
    }

    @Override
    public String toString() {
        return getBenefitCode();
    }
}
