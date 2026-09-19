package com.aman.firstclubmembership2.config;

import com.aman.firstclubmembership2.benefit.EarlyAccessBenefit;
import com.aman.firstclubmembership2.benefit.FreeDeliveryBenefit;
import com.aman.firstclubmembership2.benefit.PercentageDiscountBenefit;
import com.aman.firstclubmembership2.benefit.PrioritySupportBenefit;
import com.aman.firstclubmembership2.domain.MembershipPlan;
import com.aman.firstclubmembership2.domain.MembershipTier;
import com.aman.firstclubmembership2.enums.BillingCycle;
import com.aman.firstclubmembership2.enums.TierLevel;
import com.aman.firstclubmembership2.repository.PlanRepository;
import com.aman.firstclubmembership2.repository.TierRepository;
import com.aman.firstclubmembership2.rule.CohortRule;
import com.aman.firstclubmembership2.rule.OrderCountRule;
import com.aman.firstclubmembership2.rule.OrderValueRule;
import com.aman.firstclubmembership2.store.IdGenerator;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

/** Seeds the demo catalog (plans, tiers, criteria, benefits) shared by {@code Application} and tests. */
public final class CatalogSeeder {

    public static final String MONTHLY_PLAN_ID = "PLAN_MONTHLY";
    public static final String QUARTERLY_PLAN_ID = "PLAN_QUARTERLY";
    public static final String YEARLY_PLAN_ID = "PLAN_YEARLY";

    private CatalogSeeder() {
    }

    public static void seed(PlanRepository planRepository, TierRepository tierRepository,
                             IdGenerator idGenerator) {
        seedPlans(planRepository);
        seedTiers(tierRepository, idGenerator);
    }

    private static void seedPlans(PlanRepository planRepository) {
        planRepository.save(new MembershipPlan(
                MONTHLY_PLAN_ID, "Monthly Pass", BillingCycle.MONTHLY, new BigDecimal("199.00")));
        planRepository.save(new MembershipPlan(
                QUARTERLY_PLAN_ID, "Quarterly Pass", BillingCycle.QUARTERLY, new BigDecimal("549.00")));
        planRepository.save(new MembershipPlan(
                YEARLY_PLAN_ID, "Yearly Pass", BillingCycle.ANNUAL, new BigDecimal("1999.00")));
    }

    private static void seedTiers(TierRepository tierRepository, IdGenerator idGenerator) {
        // Silver: default baseline tier, no rules required. Flat discount, no free delivery.
        tierRepository.save(new MembershipTier(
                idGenerator.nextTierId(), TierLevel.SILVER, "Silver Member",
                List.of(
                        new PercentageDiscountBenefit(new BigDecimal("5"), Set.of())
                ),
                List.of()
        ));

        // Gold: requires >= 5 orders OR >= $2000 total order value (any one rule qualifies).
        // Discount restricted to selected categories; free delivery above a minimum order value.
        tierRepository.save(new MembershipTier(
                idGenerator.nextTierId(), TierLevel.GOLD, "Gold Member",
                List.of(
                        new PercentageDiscountBenefit(new BigDecimal("10"), Set.of("ELECTRONICS", "FASHION")),
                        new FreeDeliveryBenefit(new BigDecimal("499.00")),
                        new EarlyAccessBenefit()
                ),
                List.of(new OrderCountRule(5), new OrderValueRule(new BigDecimal("2000.00")))
        ));

        // Platinum: requires >= 15 orders OR "VIP_CLUB" cohort tag.
        // Best discount on all categories, always-free delivery, and the entitlement-only perks.
        tierRepository.save(new MembershipTier(
                idGenerator.nextTierId(), TierLevel.PLATINUM, "Platinum Member",
                List.of(
                        new PercentageDiscountBenefit(new BigDecimal("20"), Set.of()),
                        new FreeDeliveryBenefit(),
                        new EarlyAccessBenefit(),
                        new PrioritySupportBenefit()
                ),
                List.of(new OrderCountRule(15), new CohortRule("VIP_CLUB"))
        ));
    }
}
