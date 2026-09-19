package com.aman.firstclubmembership2.config;

import com.aman.firstclubmembership2.domain.MembershipPlan;
import com.aman.firstclubmembership2.domain.MembershipTier;
import com.aman.firstclubmembership2.enums.BillingCycle;
import com.aman.firstclubmembership2.enums.TierLevel;
import com.aman.firstclubmembership2.rule.AnyOfRule;
import com.aman.firstclubmembership2.rule.CohortRule;
import com.aman.firstclubmembership2.rule.OrderCountRule;
import com.aman.firstclubmembership2.rule.OrderValueRule;
import com.aman.firstclubmembership2.store.DataStore;

import java.math.BigDecimal;
import java.util.List;

/** Seeds the demo catalog (plans, tiers, criteria) shared by {@code Application} and tests. */
public final class CatalogSeeder {

    public static final String MONTHLY_PLAN_ID = "PLAN_MONTHLY";
    public static final String QUARTERLY_PLAN_ID = "PLAN_QUARTERLY";
    public static final String YEARLY_PLAN_ID = "PLAN_YEARLY";

    private CatalogSeeder() {
    }

    public static void seed(DataStore dataStore) {
        seedPlans(dataStore);
        seedTiers(dataStore);
    }

    private static void seedPlans(DataStore dataStore) {
        dataStore.plans.put(MONTHLY_PLAN_ID, new MembershipPlan(
                MONTHLY_PLAN_ID, "Monthly Pass", BillingCycle.MONTHLY, new BigDecimal("199.00")));
        dataStore.plans.put(QUARTERLY_PLAN_ID, new MembershipPlan(
                QUARTERLY_PLAN_ID, "Quarterly Pass", BillingCycle.QUARTERLY, new BigDecimal("549.00")));
        dataStore.plans.put(YEARLY_PLAN_ID, new MembershipPlan(
                YEARLY_PLAN_ID, "Yearly Pass", BillingCycle.ANNUAL, new BigDecimal("1999.00")));
    }

    private static void seedTiers(DataStore dataStore) {
        // Silver: default baseline tier, no rules required
        dataStore.tiers.put(TierLevel.SILVER.name(), new MembershipTier(
                dataStore.idGenerator.nextTierId(), TierLevel.SILVER, "Silver Member",
                List.of("5% Discount"), List.of()
        ));

        // Gold: requires >= 5 orders AND >= $2000 total order value
        dataStore.tiers.put(TierLevel.GOLD.name(), new MembershipTier(
                dataStore.idGenerator.nextTierId(), TierLevel.GOLD, "Gold Member",
                List.of("10% Discount", "Free Delivery"),
                List.of(new OrderCountRule(5), new OrderValueRule(new BigDecimal("2000.00")))
        ));

        // Platinum: requires >= 15 orders OR "VIP_CLUB" cohort tag
        dataStore.tiers.put(TierLevel.PLATINUM.name(), new MembershipTier(
                dataStore.idGenerator.nextTierId(), TierLevel.PLATINUM, "Platinum Member",
                List.of("20% Discount", "Free Delivery", "Priority Support"),
                List.of(new AnyOfRule(List.of(new OrderCountRule(15), new CohortRule("VIP_CLUB"))))
        ));
    }
}
