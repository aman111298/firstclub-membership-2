package com.aman.firstclubmembership2.pricing;

import com.aman.firstclubmembership2.domain.MembershipPlan;
import com.aman.firstclubmembership2.domain.MembershipTier;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Price = plan price x the tier's configured multiplier (e.g. Silver 1.0x, Gold 1.5x, Platinum 2.0x). */
public class MultiplierTierPricingStrategy implements TierPricingStrategy {

    @Override
    public BigDecimal priceFor(MembershipPlan plan, MembershipTier tier) {
        return plan.getPrice()
                .multiply(tier.getPriceMultiplier())
                .setScale(2, RoundingMode.HALF_UP);
    }
}
