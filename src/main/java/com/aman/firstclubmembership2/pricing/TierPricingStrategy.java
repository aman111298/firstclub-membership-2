package com.aman.firstclubmembership2.pricing;

import com.aman.firstclubmembership2.domain.MembershipPlan;
import com.aman.firstclubmembership2.domain.MembershipTier;

import java.math.BigDecimal;

/**
 * Decides what a plan + tier combination costs. Pluggable so the pricing model (multiplier,
 * fixed add-on, full plan x tier price matrix, promotions) can change without touching the service.
 */
public interface TierPricingStrategy {
    BigDecimal priceFor(MembershipPlan plan, MembershipTier tier);
}
