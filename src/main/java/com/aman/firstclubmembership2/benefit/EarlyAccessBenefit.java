package com.aman.firstclubmembership2.benefit;

import com.aman.firstclubmembership2.enums.BenefitType;
import com.aman.firstclubmembership2.model.OrderBenefitsResult;
import com.aman.firstclubmembership2.model.OrderContext;

/** Grants early access when the order is flagged as an exclusive-deal / early-sale item. */
public class EarlyAccessBenefit implements MembershipBenefit {

    @Override
    public BenefitType getType() {
        return BenefitType.EARLY_ACCESS;
    }

    @Override
    public void apply(OrderContext context, OrderBenefitsResult result) {
        if (context.isExclusiveDeal()) {
            result.setEarlyAccessGranted(true);
            result.addSummary("Unlocked early access to exclusive deals");
        }
    }

    @Override
    public String toString() {
        return getType().name();
    }
}
