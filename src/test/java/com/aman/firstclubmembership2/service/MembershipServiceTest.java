package com.aman.firstclubmembership2.service;

import com.aman.firstclubmembership2.benefit.FreeDeliveryBenefit;
import com.aman.firstclubmembership2.benefit.MembershipBenefit;
import com.aman.firstclubmembership2.benefit.PercentageDiscountBenefit;
import com.aman.firstclubmembership2.benefit.PrioritySupportBenefit;
import com.aman.firstclubmembership2.concurrency.UserLockManager;
import com.aman.firstclubmembership2.config.CatalogSeeder;
import com.aman.firstclubmembership2.domain.MembershipPlan;
import com.aman.firstclubmembership2.domain.MembershipTier;
import com.aman.firstclubmembership2.domain.UserSubscription;
import com.aman.firstclubmembership2.enums.BillingCycle;
import com.aman.firstclubmembership2.enums.SubscriptionStatus;
import com.aman.firstclubmembership2.enums.TierLevel;
import com.aman.firstclubmembership2.exception.PaymentFailedException;
import com.aman.firstclubmembership2.model.OrderBenefitsResult;
import com.aman.firstclubmembership2.model.OrderContext;
import com.aman.firstclubmembership2.model.UserMetrics;
import com.aman.firstclubmembership2.repository.InMemoryPaymentLogRepository;
import com.aman.firstclubmembership2.repository.InMemoryPlanRepository;
import com.aman.firstclubmembership2.repository.InMemorySubscriptionRepository;
import com.aman.firstclubmembership2.repository.InMemoryTierRepository;
import com.aman.firstclubmembership2.repository.PaymentLogRepository;
import com.aman.firstclubmembership2.repository.PlanRepository;
import com.aman.firstclubmembership2.repository.SubscriptionRepository;
import com.aman.firstclubmembership2.repository.TierRepository;
import com.aman.firstclubmembership2.store.IdGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MembershipServiceTest {

    private IdGenerator idGenerator;
    private PlanRepository planRepository;
    private TierRepository tierRepository;
    private SubscriptionRepository subscriptionRepository;
    private PaymentLogRepository paymentLogRepository;
    private UserLockManager userLockManager;
    private MembershipService service;

    @BeforeEach
    void setUp() {
        idGenerator = new IdGenerator();
        planRepository = new InMemoryPlanRepository();
        tierRepository = new InMemoryTierRepository();
        subscriptionRepository = new InMemorySubscriptionRepository();
        paymentLogRepository = new InMemoryPaymentLogRepository();
        userLockManager = new UserLockManager();

        CatalogSeeder.seed(planRepository, tierRepository, idGenerator);

        service = new MembershipService(planRepository, tierRepository, subscriptionRepository, paymentLogRepository, idGenerator, userLockManager);
    }

    private static UserMetrics metrics(int orderCount, String orderValue, String... cohorts) {
        return new UserMetrics("USER_1", orderCount, new BigDecimal(orderValue), Set.of(cohorts));
    }

    // ---------------------------------------------------------------
    // Requirement 1: Membership Plans (Monthly / Quarterly / Yearly, each priced)
    // ---------------------------------------------------------------

    @Test
    void catalog_offersMonthlyQuarterlyAndYearlyPlansWithDistinctPricing() {
        Object plansObj = service.getPlansAndTiers().get("plans");
        @SuppressWarnings("unchecked")
        var plans = (java.util.Collection<MembershipPlan>) plansObj;

        assertEquals(3, plans.size());
        assertTrue(plans.stream().anyMatch(p -> p.getBillingCycle() == BillingCycle.MONTHLY));
        assertTrue(plans.stream().anyMatch(p -> p.getBillingCycle() == BillingCycle.QUARTERLY));
        assertTrue(plans.stream().anyMatch(p -> p.getBillingCycle() == BillingCycle.ANNUAL));

        Set<BigDecimal> distinctPrices = plans.stream().map(MembershipPlan::getPrice).collect(java.util.stream.Collectors.toSet());
        assertEquals(3, distinctPrices.size(), "each plan should have its own price");
    }

    @Test
    void catalog_offersSilverGoldAndPlatinumTiers() {
        Object tiersObj = service.getPlansAndTiers().get("tiers");
        @SuppressWarnings("unchecked")
        var tiers = (java.util.Collection<MembershipTier>) tiersObj;

        assertEquals(3, tiers.size());
        assertTrue(tiers.stream().anyMatch(t -> t.getLevel() == TierLevel.SILVER));
        assertTrue(tiers.stream().anyMatch(t -> t.getLevel() == TierLevel.GOLD));
        assertTrue(tiers.stream().anyMatch(t -> t.getLevel() == TierLevel.PLATINUM));
    }

    // ---------------------------------------------------------------
    // Requirement 3: Subscribe to a plan (plan + tier)
    // ---------------------------------------------------------------

    @Test
    void subscribe_toMonthlyPlan_createsActiveSubscriptionEndingIn30Days() {
        UserSubscription sub = service.subscribe("USER_1", CatalogSeeder.MONTHLY_PLAN_ID, TierLevel.SILVER, "CREDIT_CARD");

        assertEquals(SubscriptionStatus.ACTIVE, sub.getStatus());
        assertEquals(TierLevel.SILVER, sub.getTierLevel());
        assertEquals("SUCCESS", sub.getPaymentContext().status());
        assertDurationDays(sub.getStartDate(), sub.getEndDate(), 30);
    }

    @Test
    void subscribe_toQuarterlyPlan_endsIn90Days() {
        UserSubscription sub = service.subscribe("USER_1", CatalogSeeder.QUARTERLY_PLAN_ID, TierLevel.SILVER, "CREDIT_CARD");

        assertDurationDays(sub.getStartDate(), sub.getEndDate(), 90);
    }

    @Test
    void subscribe_toYearlyPlan_endsIn365Days() {
        UserSubscription sub = service.subscribe("USER_1", CatalogSeeder.YEARLY_PLAN_ID, TierLevel.SILVER, "CREDIT_CARD");

        assertDurationDays(sub.getStartDate(), sub.getEndDate(), 365);
    }

    private static void assertDurationDays(LocalDateTime start, LocalDateTime end, long expectedDays) {
        assertEquals(expectedDays, Duration.between(start, end).toDays());
    }

    @Test
    void subscribe_unknownPlanId_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> service.subscribe("USER_1", "PLAN_DOES_NOT_EXIST", TierLevel.SILVER, "CREDIT_CARD"));
    }

    @Test
    void subscribe_tierNotConfiguredInCatalog_throwsIllegalArgumentException() {
        // A catalog where only SILVER has been configured - GOLD is a valid enum value but not an offered tier.
        PlanRepository sparsePlans = new InMemoryPlanRepository();
        sparsePlans.save(new MembershipPlan(CatalogSeeder.MONTHLY_PLAN_ID, "Monthly Pass", BillingCycle.MONTHLY, new BigDecimal("199.00")));
        TierRepository sparseTiers = new InMemoryTierRepository();
        sparseTiers.save(new MembershipTier(idGenerator.nextTierId(), TierLevel.SILVER, "Silver", List.of(), List.of()));
        MembershipService sparseService = new MembershipService(
                sparsePlans, sparseTiers, new InMemorySubscriptionRepository(), new InMemoryPaymentLogRepository(), idGenerator, userLockManager);

        assertThrows(IllegalArgumentException.class,
                () -> sparseService.subscribe("USER_1", CatalogSeeder.MONTHLY_PLAN_ID, TierLevel.GOLD, "CREDIT_CARD"));
    }

    @Test
    void subscribe_whenUserAlreadyHasActiveSubscription_throwsIllegalStateException() {
        service.subscribe("USER_1", CatalogSeeder.MONTHLY_PLAN_ID, TierLevel.SILVER, "CREDIT_CARD");

        assertThrows(IllegalStateException.class,
                () -> service.subscribe("USER_1", CatalogSeeder.MONTHLY_PLAN_ID, TierLevel.SILVER, "CREDIT_CARD"));
    }

    @Test
    void subscribe_afterPriorSubscriptionWasCancelled_isAllowedAgain() {
        service.subscribe("USER_1", CatalogSeeder.MONTHLY_PLAN_ID, TierLevel.SILVER, "CREDIT_CARD");
        service.cancelSubscription("USER_1");

        UserSubscription resubscribed = service.subscribe("USER_1", CatalogSeeder.MONTHLY_PLAN_ID, TierLevel.SILVER, "CREDIT_CARD");

        assertEquals(SubscriptionStatus.ACTIVE, resubscribed.getStatus());
    }

    @Test
    void subscribe_paymentFails_throwsPaymentFailedExceptionAndDoesNotCreateSubscription() {
        planRepository.save(new MembershipPlan("PLAN_BROKEN", "Broken Plan", BillingCycle.MONTHLY, new BigDecimal("-1.00")));

        assertThrows(PaymentFailedException.class,
                () -> service.subscribe("USER_1", "PLAN_BROKEN", TierLevel.SILVER, "CREDIT_CARD"));
        assertTrue(service.getSubscription("USER_1").isEmpty());
    }

    /**
     * Characterization test: subscribe() currently does not check MembershipTier.qualifies()
     * against the user's metrics - it only checks that the tier exists in the catalog. A user
     * can self-select Platinum with zero orders and no cohort. See conversation notes on
     * whether eligibility should gate tier selection at subscribe time.
     */
    @Test
    void subscribe_currentlyDoesNotEnforceTierEligibilityCriteria() {
        UserSubscription sub = service.subscribe("USER_1", CatalogSeeder.MONTHLY_PLAN_ID, TierLevel.PLATINUM, "CREDIT_CARD");

        assertEquals(TierLevel.PLATINUM, sub.getTierLevel());
    }

    // ---------------------------------------------------------------
    // Requirement 3: Upgrade / downgrade a subscription's tier
    // ---------------------------------------------------------------

    @Test
    void changeTierManual_toHigherTier_upgradesSubscription() {
        service.subscribe("USER_1", CatalogSeeder.MONTHLY_PLAN_ID, TierLevel.SILVER, "CREDIT_CARD");

        UserSubscription upgraded = service.changeTierManual("USER_1", TierLevel.GOLD);

        assertEquals(TierLevel.GOLD, upgraded.getTierLevel());
    }

    @Test
    void changeTierManual_toLowerTier_downgradesSubscription() {
        service.subscribe("USER_1", CatalogSeeder.MONTHLY_PLAN_ID, TierLevel.PLATINUM, "CREDIT_CARD");

        UserSubscription downgraded = service.changeTierManual("USER_1", TierLevel.SILVER);

        assertEquals(TierLevel.SILVER, downgraded.getTierLevel());
    }

    @Test
    void changeTierManual_sameTier_isNoOp() {
        service.subscribe("USER_1", CatalogSeeder.MONTHLY_PLAN_ID, TierLevel.GOLD, "CREDIT_CARD");

        UserSubscription result = service.changeTierManual("USER_1", TierLevel.GOLD);

        assertEquals(TierLevel.GOLD, result.getTierLevel());
    }

    @Test
    void changeTierManual_noActiveSubscription_throwsIllegalStateException() {
        assertThrows(IllegalStateException.class, () -> service.changeTierManual("USER_1", TierLevel.GOLD));
    }

    @Test
    void changeTierManual_afterCancellation_throwsIllegalStateException() {
        service.subscribe("USER_1", CatalogSeeder.MONTHLY_PLAN_ID, TierLevel.SILVER, "CREDIT_CARD");
        service.cancelSubscription("USER_1");

        assertThrows(IllegalStateException.class, () -> service.changeTierManual("USER_1", TierLevel.GOLD));
    }

    // ---------------------------------------------------------------
    // Requirement 3: Cancel a subscription
    // ---------------------------------------------------------------

    @Test
    void cancelSubscription_setsStatusToCancelled() {
        service.subscribe("USER_1", CatalogSeeder.MONTHLY_PLAN_ID, TierLevel.SILVER, "CREDIT_CARD");

        service.cancelSubscription("USER_1");

        assertEquals(SubscriptionStatus.CANCELLED, service.getSubscription("USER_1").orElseThrow().getStatus());
    }

    @Test
    void cancelSubscription_noSubscription_throwsIllegalStateException() {
        assertThrows(IllegalStateException.class, () -> service.cancelSubscription("USER_1"));
    }

    /**
     * Characterization test: isExpired() returns true for ANY non-ACTIVE status, so cancelling
     * revokes access immediately rather than "at period end" - even though endDate is untouched.
     * Flagged in conversation notes as worth confirming against the intended cancellation policy.
     */
    @Test
    void cancelSubscription_currentlyMakesIsExpiredTrueImmediately_evenThoughEndDateIsUnchanged() {
        UserSubscription sub = service.subscribe("USER_1", CatalogSeeder.YEARLY_PLAN_ID, TierLevel.SILVER, "CREDIT_CARD");
        LocalDateTime originalEndDate = sub.getEndDate();

        service.cancelSubscription("USER_1");

        UserSubscription cancelled = service.getSubscription("USER_1").orElseThrow();
        assertEquals(originalEndDate, cancelled.getEndDate(), "endDate is untouched by cancellation");
        assertTrue(cancelled.isExpired(), "but isExpired() is already true, well before endDate");
    }

    // ---------------------------------------------------------------
    // Requirement 3: Track current membership and expiry
    // ---------------------------------------------------------------

    @Test
    void getSubscription_returnsEmpty_whenUserHasNoSubscription() {
        assertTrue(service.getSubscription("USER_1").isEmpty());
    }

    @Test
    void getSubscription_returnsCurrentSubscriptionDetails() {
        service.subscribe("USER_1", CatalogSeeder.MONTHLY_PLAN_ID, TierLevel.GOLD, "CREDIT_CARD");

        Optional<UserSubscription> current = service.getSubscription("USER_1");

        assertTrue(current.isPresent());
        assertEquals(TierLevel.GOLD, current.get().getTierLevel());
        assertEquals(SubscriptionStatus.ACTIVE, current.get().getStatus());
    }

    // ---------------------------------------------------------------
    // Requirement 4: Membership Tiers move based on criteria
    // ---------------------------------------------------------------

    @Test
    void evaluateEligibleTier_noCriteriaMet_returnsSilver() {
        assertEquals(TierLevel.SILVER, service.evaluateEligibleTier(metrics(0, "0.00")));
    }

    @Test
    void evaluateEligibleTier_orderCountAndValueBothMet_returnsGold() {
        assertEquals(TierLevel.GOLD, service.evaluateEligibleTier(metrics(5, "2000.00")));
    }

    @Test
    void evaluateEligibleTier_onlyOrderCountMet_returnsGold() {
        // Gold's rules are OR'd: count alone is enough, value isn't required too.
        assertEquals(TierLevel.GOLD, service.evaluateEligibleTier(metrics(5, "0.00")));
    }

    @Test
    void evaluateEligibleTier_onlyOrderValueMet_returnsGold() {
        assertEquals(TierLevel.GOLD, service.evaluateEligibleTier(metrics(0, "2000.00")));
    }

    @Test
    void evaluateEligibleTier_cohortTagAlone_returnsPlatinum() {
        assertEquals(TierLevel.PLATINUM, service.evaluateEligibleTier(metrics(0, "0.00", "VIP_CLUB")));
    }

    @Test
    void evaluateEligibleTier_highOrderCountAlone_returnsPlatinum() {
        assertEquals(TierLevel.PLATINUM, service.evaluateEligibleTier(metrics(15, "0.00")));
    }

    @Test
    void evaluateAndUpdateUserTier_upgradesActiveSubscriptionWhenMetricsImprove() {
        service.subscribe("USER_1", CatalogSeeder.MONTHLY_PLAN_ID, TierLevel.SILVER, "CREDIT_CARD");

        Optional<UserSubscription> result = service.evaluateAndUpdateUserTier(metrics(6, "2500.00"));

        assertTrue(result.isPresent());
        assertEquals(TierLevel.GOLD, result.get().getTierLevel());
    }

    @Test
    void evaluateAndUpdateUserTier_downgradesActiveSubscriptionWhenMetricsDrop() {
        service.subscribe("USER_1", CatalogSeeder.MONTHLY_PLAN_ID, TierLevel.PLATINUM, "CREDIT_CARD");

        Optional<UserSubscription> result = service.evaluateAndUpdateUserTier(metrics(0, "0.00"));

        assertTrue(result.isPresent());
        assertEquals(TierLevel.SILVER, result.get().getTierLevel());
    }

    @Test
    void evaluateAndUpdateUserTier_noChangeWhenAlreadyAtQualifiedTier() {
        service.subscribe("USER_1", CatalogSeeder.MONTHLY_PLAN_ID, TierLevel.SILVER, "CREDIT_CARD");

        Optional<UserSubscription> result = service.evaluateAndUpdateUserTier(metrics(0, "0.00"));

        assertTrue(result.isPresent());
        assertEquals(TierLevel.SILVER, result.get().getTierLevel());
    }

    @Test
    void evaluateAndUpdateUserTier_noActiveSubscription_returnsEmpty() {
        assertTrue(service.evaluateAndUpdateUserTier(metrics(6, "2500.00")).isEmpty());
    }

    @Test
    void evaluateAndUpdateUserTier_afterCancellation_returnsEmpty() {
        service.subscribe("USER_1", CatalogSeeder.MONTHLY_PLAN_ID, TierLevel.SILVER, "CREDIT_CARD");
        service.cancelSubscription("USER_1");

        assertFalse(service.evaluateAndUpdateUserTier(metrics(6, "2500.00")).isPresent());
    }

    // ---------------------------------------------------------------
    // Membership Benefits: configurable catalog + evaluation
    // ---------------------------------------------------------------

    private static OrderContext order(String subtotal, String category, String standardDeliveryFee,
                                       boolean isExclusiveDeal, boolean isPrioritySupportRequested) {
        return new OrderContext(new BigDecimal(subtotal), category, new BigDecimal(standardDeliveryFee),
                isExclusiveDeal, isPrioritySupportRequested);
    }

    @Test
    void getBenefits_noActiveSubscription_returnsEmptyList() {
        assertTrue(service.getBenefits("USER_1").isEmpty());
    }

    @Test
    void getBenefits_returnsTheSubscribedTiersConfiguredBenefits() {
        service.subscribe("USER_1", CatalogSeeder.MONTHLY_PLAN_ID, TierLevel.PLATINUM, "CREDIT_CARD");

        List<MembershipBenefit> benefits = service.getBenefits("USER_1");

        assertTrue(benefits.stream().anyMatch(b -> b instanceof PercentageDiscountBenefit));
        assertTrue(benefits.stream().anyMatch(b -> b instanceof FreeDeliveryBenefit));
        assertTrue(benefits.stream().anyMatch(b -> b instanceof PrioritySupportBenefit));
    }

    @Test
    void getBenefits_afterCancellation_returnsEmptyList() {
        service.subscribe("USER_1", CatalogSeeder.MONTHLY_PLAN_ID, TierLevel.GOLD, "CREDIT_CARD");
        service.cancelSubscription("USER_1");

        assertTrue(service.getBenefits("USER_1").isEmpty());
    }

    @Test
    void evaluateBenefits_silver_flatDiscountOnAnyCategoryNoFreeDelivery() {
        service.subscribe("USER_1", CatalogSeeder.MONTHLY_PLAN_ID, TierLevel.SILVER, "CREDIT_CARD");

        OrderBenefitsResult result = service.evaluateBenefits("USER_1", order("1000.00", "GROCERY", "40.00", false, false));

        assertEquals(new BigDecimal("50.00"), result.getDiscountAmount());
        assertEquals(new BigDecimal("40.00"), result.getDeliveryFee());
        assertFalse(result.isEarlyAccessGranted());
        assertFalse(result.isPrioritySupportGranted());
    }

    @Test
    void evaluateBenefits_gold_discountOnlyOnConfiguredCategories() {
        service.subscribe("USER_1", CatalogSeeder.MONTHLY_PLAN_ID, TierLevel.GOLD, "CREDIT_CARD");

        OrderBenefitsResult electronics = service.evaluateBenefits("USER_1", order("1000.00", "ELECTRONICS", "40.00", false, false));
        assertEquals(new BigDecimal("100.00"), electronics.getDiscountAmount());

        OrderBenefitsResult grocery = service.evaluateBenefits("USER_1", order("1000.00", "GROCERY", "40.00", false, false));
        assertEquals(BigDecimal.ZERO, grocery.getDiscountAmount());
    }

    @Test
    void evaluateBenefits_gold_freeDeliveryOnlyAboveThreshold() {
        service.subscribe("USER_1", CatalogSeeder.MONTHLY_PLAN_ID, TierLevel.GOLD, "CREDIT_CARD");

        OrderBenefitsResult belowThreshold = service.evaluateBenefits("USER_1", order("100.00", "GROCERY", "40.00", false, false));
        assertEquals(new BigDecimal("40.00"), belowThreshold.getDeliveryFee());

        OrderBenefitsResult atThreshold = service.evaluateBenefits("USER_1", order("499.00", "GROCERY", "40.00", false, false));
        assertEquals(BigDecimal.ZERO, atThreshold.getDeliveryFee());
    }

    @Test
    void evaluateBenefits_platinum_alwaysFreeDeliveryAndGrantsEntitlementsWhenRequested() {
        service.subscribe("USER_1", CatalogSeeder.MONTHLY_PLAN_ID, TierLevel.PLATINUM, "CREDIT_CARD");

        OrderBenefitsResult result = service.evaluateBenefits("USER_1", order("10.00", "GROCERY", "40.00", true, true));

        assertEquals(BigDecimal.ZERO, result.getDeliveryFee());
        assertTrue(result.isEarlyAccessGranted());
        assertTrue(result.isPrioritySupportGranted());
    }

    @Test
    void evaluateBenefits_entitlementsNotGrantedWhenNotRequested() {
        service.subscribe("USER_1", CatalogSeeder.MONTHLY_PLAN_ID, TierLevel.PLATINUM, "CREDIT_CARD");

        OrderBenefitsResult result = service.evaluateBenefits("USER_1", order("10.00", "GROCERY", "40.00", false, false));

        assertFalse(result.isEarlyAccessGranted());
        assertFalse(result.isPrioritySupportGranted());
    }

    @Test
    void evaluateBenefits_noActiveSubscription_grantsNothing() {
        OrderBenefitsResult result = service.evaluateBenefits("USER_1", order("1000.00", "ELECTRONICS", "40.00", true, true));

        assertEquals(BigDecimal.ZERO, result.getDiscountAmount());
        assertEquals(new BigDecimal("40.00"), result.getDeliveryFee(), "standard fee passes through untouched");
        assertFalse(result.isEarlyAccessGranted());
        assertFalse(result.isPrioritySupportGranted());
    }
}
