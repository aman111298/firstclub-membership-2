package com.aman.firstclubmembership2.benefit;

import com.aman.firstclubmembership2.enums.BenefitType;
import com.aman.firstclubmembership2.model.OrderBenefitsResult;
import com.aman.firstclubmembership2.model.OrderContext;

import java.math.BigDecimal;
import java.util.Set;

/** Percentage discount on the order subtotal, optionally restricted to a set of categories (empty = all categories). */
public class PercentageDiscountBenefit implements MembershipBenefit {

    private final BigDecimal discountPercent;
    private final Set<String> eligibleCategories;

    public PercentageDiscountBenefit(BigDecimal discountPercent, Set<String> eligibleCategories) {
        this.discountPercent = discountPercent;
        this.eligibleCategories = Set.copyOf(eligibleCategories);
    }

    @Override
    public BenefitType getType() {
        return BenefitType.PERCENT_DISCOUNT;
    }

    @Override
    public void apply(OrderContext context, OrderBenefitsResult result) {
        if (eligibleCategories.isEmpty() || eligibleCategories.contains(context.category())) {
            BigDecimal discount = context.subtotal()
                    .multiply(discountPercent)
                    .divide(new BigDecimal("100"));
            result.addDiscountAmount(discount);
            result.addSummary("Applied " + discountPercent + "% discount");
        }
    }

    @Override
    public String toString() {
        return getType() + "_" + discountPercent + "%";
    }
}
