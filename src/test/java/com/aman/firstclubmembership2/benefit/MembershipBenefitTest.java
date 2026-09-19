package com.aman.firstclubmembership2.benefit;

import com.aman.firstclubmembership2.model.OrderBenefitsResult;
import com.aman.firstclubmembership2.model.OrderContext;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MembershipBenefitTest {

    private static OrderContext context(String subtotal, String category, String deliveryFee,
                                         boolean exclusiveDeal, boolean prioritySupportRequested) {
        return new OrderContext(new BigDecimal(subtotal), category, new BigDecimal(deliveryFee),
                exclusiveDeal, prioritySupportRequested);
    }

    @Test
    void percentageDiscount_appliesToAllCategories_whenNoneConfigured() {
        PercentageDiscountBenefit benefit = new PercentageDiscountBenefit(new BigDecimal("10"), Set.of());
        OrderBenefitsResult result = new OrderBenefitsResult(new BigDecimal("40.00"));

        benefit.apply(context("1000.00", "GROCERY", "40.00", false, false), result);

        assertEquals(new BigDecimal("100.00"), result.getDiscountAmount());
    }

    @Test
    void percentageDiscount_restrictedToConfiguredCategories() {
        PercentageDiscountBenefit benefit = new PercentageDiscountBenefit(new BigDecimal("10"), Set.of("ELECTRONICS"));

        OrderBenefitsResult matching = new OrderBenefitsResult(BigDecimal.ZERO);
        benefit.apply(context("1000.00", "ELECTRONICS", "0.00", false, false), matching);
        assertEquals(new BigDecimal("100.00"), matching.getDiscountAmount());

        OrderBenefitsResult nonMatching = new OrderBenefitsResult(BigDecimal.ZERO);
        benefit.apply(context("1000.00", "GROCERY", "0.00", false, false), nonMatching);
        assertEquals(BigDecimal.ZERO, nonMatching.getDiscountAmount());
    }

    @Test
    void freeDelivery_noMinimum_alwaysWaivesFee() {
        FreeDeliveryBenefit benefit = new FreeDeliveryBenefit();
        OrderBenefitsResult result = new OrderBenefitsResult(new BigDecimal("40.00"));

        benefit.apply(context("1.00", "GROCERY", "40.00", false, false), result);

        assertEquals(BigDecimal.ZERO, result.getDeliveryFee());
    }

    @Test
    void freeDelivery_withMinimum_onlyWaivesFeeAtOrAboveThreshold() {
        FreeDeliveryBenefit benefit = new FreeDeliveryBenefit(new BigDecimal("499.00"));

        OrderBenefitsResult below = new OrderBenefitsResult(new BigDecimal("40.00"));
        benefit.apply(context("100.00", "GROCERY", "40.00", false, false), below);
        assertEquals(new BigDecimal("40.00"), below.getDeliveryFee());

        OrderBenefitsResult atThreshold = new OrderBenefitsResult(new BigDecimal("40.00"));
        benefit.apply(context("499.00", "GROCERY", "40.00", false, false), atThreshold);
        assertEquals(BigDecimal.ZERO, atThreshold.getDeliveryFee());
    }

    @Test
    void earlyAccess_grantedOnlyForExclusiveDeals() {
        EarlyAccessBenefit benefit = new EarlyAccessBenefit();

        OrderBenefitsResult granted = new OrderBenefitsResult(BigDecimal.ZERO);
        benefit.apply(context("1.00", "GROCERY", "0.00", true, false), granted);
        assertTrue(granted.isEarlyAccessGranted());

        OrderBenefitsResult notGranted = new OrderBenefitsResult(BigDecimal.ZERO);
        benefit.apply(context("1.00", "GROCERY", "0.00", false, false), notGranted);
        assertFalse(notGranted.isEarlyAccessGranted());
    }

    @Test
    void prioritySupport_grantedOnlyWhenRequested() {
        PrioritySupportBenefit benefit = new PrioritySupportBenefit();

        OrderBenefitsResult granted = new OrderBenefitsResult(BigDecimal.ZERO);
        benefit.apply(context("1.00", "GROCERY", "0.00", false, true), granted);
        assertTrue(granted.isPrioritySupportGranted());

        OrderBenefitsResult notGranted = new OrderBenefitsResult(BigDecimal.ZERO);
        benefit.apply(context("1.00", "GROCERY", "0.00", false, false), notGranted);
        assertFalse(notGranted.isPrioritySupportGranted());
    }
}
