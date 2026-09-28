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
import com.aman.firstclubmembership2.model.PaymentContext;
import com.aman.firstclubmembership2.model.UserMetrics;
import com.aman.firstclubmembership2.pricing.MultiplierTierPricingStrategy;
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
import java.util.ArrayList;
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
    private List<PaymentContext> payments;
    private MembershipService service;

    @BeforeEach
    void setUp() {
        idGenerator = new IdGenerator();
        planRepository = new InMemoryPlanRepository();
        tierRepository = new InMemoryTierRepository();
        subscriptionRepository = new InMemorySubscriptionRepository();
        payments = new ArrayList<>();
        paymentLogRepository = payments::add; // captures every charge so tests can assert amounts
        userLockManager = new UserLockManager();

        CatalogSeeder.seed(planRepository, tierRepository, idGenerator);

        service = new MembershipService(planRepository, tierRepository, subscriptionRepository, paymentLogRepository,
                idGenerator, userLockManager, new MultiplierTierPricingStrategy());
    }

    private BigDecimal lastChargedAmount() {
        return payments.get(payments.size() - 1).amount();
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
        assertEquals(TierLevel.SILVER, sub.getEffectiveTier());
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
                sparsePlans, sparseTiers, new InMemorySubscriptionRepository(), new InMemoryPaymentLogRepository(), idGenerator, userLockManager,
                new MultiplierTierPricingStrategy());

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

    // ---------------------------------------------------------------
    // Tier pricing: plan price x tier multiplier
    // ---------------------------------------------------------------

    @Test
    void getPrice_isPlanPriceTimesTierMultiplier() {
        assertEquals(new BigDecimal("199.00"), service.getPrice(CatalogSeeder.MONTHLY_PLAN_ID, TierLevel.SILVER));
        assertEquals(new BigDecimal("298.50"), service.getPrice(CatalogSeeder.MONTHLY_PLAN_ID, TierLevel.GOLD));
        assertEquals(new BigDecimal("3998.00"), service.getPrice(CatalogSeeder.YEARLY_PLAN_ID, TierLevel.PLATINUM));
    }

    @Test
    void subscribe_chargesPlanPlusTierPrice() {
        UserSubscription sub = service.subscribe("USER_1", CatalogSeeder.QUARTERLY_PLAN_ID, TierLevel.GOLD, "CREDIT_CARD");

        assertEquals(new BigDecimal("823.50"), sub.getPaymentContext().amount());
    }

    @Test
    void subscribe_givesExactlyThePurchasedTier_withoutEvaluatingCriteria() {
        // A new user with no activity can still buy Platinum - buying is separate from earning.
        UserSubscription sub = service.subscribe("USER_1", CatalogSeeder.MONTHLY_PLAN_ID, TierLevel.PLATINUM, "CREDIT_CARD");

        assertEquals(TierLevel.PLATINUM, sub.getPurchasedTier());
        assertEquals(null, sub.getEarnedTier(), "no evaluation runs at purchase time");
        assertEquals(TierLevel.PLATINUM, sub.getEffectiveTier());
    }

    // ---------------------------------------------------------------
    // Requirement 3: Upgrade (user, paid) / downgrade (admin penalty)
    // ---------------------------------------------------------------

    @Test
    void upgradeTier_raisesPurchasedTier_andChargesProratedDifference() {
        service.subscribe("USER_1", CatalogSeeder.MONTHLY_PLAN_ID, TierLevel.SILVER, "CREDIT_CARD");

        UserSubscription upgraded = service.upgradeTier("USER_1", TierLevel.GOLD, "CREDIT_CARD");

        assertEquals(TierLevel.GOLD, upgraded.getPurchasedTier());
        assertEquals(TierLevel.GOLD, upgraded.getEffectiveTier());
        // Upgraded right after buying, so almost the whole period remains: ~ (298.50 - 199.00)
        assertEquals(new BigDecimal("99.50"), lastChargedAmount());
    }

    @Test
    void upgradeTier_toSameOrLowerTier_throwsIllegalArgumentException() {
        service.subscribe("USER_1", CatalogSeeder.MONTHLY_PLAN_ID, TierLevel.GOLD, "CREDIT_CARD");

        assertThrows(IllegalArgumentException.class, () -> service.upgradeTier("USER_1", TierLevel.GOLD, "CREDIT_CARD"));
        assertThrows(IllegalArgumentException.class, () -> service.upgradeTier("USER_1", TierLevel.SILVER, "CREDIT_CARD"));
    }

    @Test
    void upgradeTier_noActiveSubscription_throwsIllegalStateException() {
        assertThrows(IllegalStateException.class, () -> service.upgradeTier("USER_1", TierLevel.GOLD, "CREDIT_CARD"));
    }

    @Test
    void upgradeTier_afterCancellation_throwsIllegalStateException() {
        service.subscribe("USER_1", CatalogSeeder.MONTHLY_PLAN_ID, TierLevel.SILVER, "CREDIT_CARD");
        service.cancelSubscription("USER_1");

        assertThrows(IllegalStateException.class, () -> service.upgradeTier("USER_1", TierLevel.GOLD, "CREDIT_CARD"));
    }

    @Test
    void adminDowngradeTier_lowersPurchasedTier_withoutCharging() {
        service.subscribe("USER_1", CatalogSeeder.MONTHLY_PLAN_ID, TierLevel.PLATINUM, "CREDIT_CARD");
        int paymentsBefore = payments.size();

        UserSubscription downgraded = service.adminDowngradeTier("USER_1", TierLevel.SILVER);

        assertEquals(TierLevel.SILVER, downgraded.getPurchasedTier());
        assertEquals(TierLevel.SILVER, downgraded.getEffectiveTier());
        assertEquals(paymentsBefore, payments.size(), "a penalty downgrade neither charges nor refunds");
    }

    @Test
    void adminDowngradeTier_clearsEarnedTier_soThePenaltyTakesEffectImmediately() {
        service.subscribe("USER_1", CatalogSeeder.MONTHLY_PLAN_ID, TierLevel.GOLD, "CREDIT_CARD");
        service.reevaluateEarnedTier(metrics(0, "0.00", "VIP_CLUB")); // earned Platinum

        UserSubscription downgraded = service.adminDowngradeTier("USER_1", TierLevel.SILVER);

        assertEquals(null, downgraded.getEarnedTier());
        assertEquals(TierLevel.SILVER, downgraded.getEffectiveTier());
    }

    @Test
    void adminDowngradeTier_toSameOrHigherTier_throwsIllegalArgumentException() {
        service.subscribe("USER_1", CatalogSeeder.MONTHLY_PLAN_ID, TierLevel.GOLD, "CREDIT_CARD");

        assertThrows(IllegalArgumentException.class, () -> service.adminDowngradeTier("USER_1", TierLevel.GOLD));
        assertThrows(IllegalArgumentException.class, () -> service.adminDowngradeTier("USER_1", TierLevel.PLATINUM));
    }

    @Test
    void adminDowngradeTier_noActiveSubscription_throwsIllegalStateException() {
        assertThrows(IllegalStateException.class, () -> service.adminDowngradeTier("USER_1", TierLevel.SILVER));
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
        assertEquals(TierLevel.GOLD, current.get().getEffectiveTier());
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
    void reevaluateEarnedTier_liftsEffectiveTierAbovePurchasedWhenMetricsImprove() {
        service.subscribe("USER_1", CatalogSeeder.MONTHLY_PLAN_ID, TierLevel.SILVER, "CREDIT_CARD");

        Optional<UserSubscription> result = service.reevaluateEarnedTier(metrics(6, "2500.00"));

        assertTrue(result.isPresent());
        assertEquals(TierLevel.SILVER, result.get().getPurchasedTier(), "purchased tier is never touched by evaluation");
        assertEquals(TierLevel.GOLD, result.get().getEarnedTier());
        assertEquals(TierLevel.GOLD, result.get().getEffectiveTier());
    }

    @Test
    void reevaluateEarnedTier_whenMetricsDrop_neverDowngradesAnEarnedTier() {
        service.subscribe("USER_1", CatalogSeeder.MONTHLY_PLAN_ID, TierLevel.SILVER, "CREDIT_CARD");
        service.reevaluateEarnedTier(metrics(15, "0.00")); // earned Platinum

        Optional<UserSubscription> result = service.reevaluateEarnedTier(metrics(0, "0.00"));

        assertEquals(TierLevel.PLATINUM, result.orElseThrow().getEarnedTier());
        assertEquals(TierLevel.PLATINUM, result.orElseThrow().getEffectiveTier(), "only an admin can downgrade");
    }

    @Test
    void reevaluateEarnedTier_neverDropsBelowThePurchasedTier() {
        service.subscribe("USER_1", CatalogSeeder.MONTHLY_PLAN_ID, TierLevel.PLATINUM, "CREDIT_CARD");

        Optional<UserSubscription> result = service.reevaluateEarnedTier(metrics(0, "0.00"));

        assertEquals(null, result.orElseThrow().getEarnedTier(), "lower qualification is not recorded");
        assertEquals(TierLevel.PLATINUM, result.orElseThrow().getEffectiveTier());
    }

    @Test
    void reevaluateEarnedTier_qualifyingForThePurchasedTierOnly_changesNothing() {
        service.subscribe("USER_1", CatalogSeeder.MONTHLY_PLAN_ID, TierLevel.GOLD, "CREDIT_CARD");

        Optional<UserSubscription> result = service.reevaluateEarnedTier(metrics(6, "0.00")); // qualifies Gold

        assertEquals(null, result.orElseThrow().getEarnedTier());
        assertEquals(TierLevel.GOLD, result.orElseThrow().getEffectiveTier());
    }

    @Test
    void reevaluateEarnedTier_neverCharges() {
        service.subscribe("USER_1", CatalogSeeder.MONTHLY_PLAN_ID, TierLevel.SILVER, "CREDIT_CARD");
        int paymentsBefore = payments.size();

        service.reevaluateEarnedTier(metrics(0, "0.00", "VIP_CLUB"));

        assertEquals(paymentsBefore, payments.size());
    }

    @Test
    void reevaluateEarnedTier_noActiveSubscription_returnsEmpty() {
        assertTrue(service.reevaluateEarnedTier(metrics(6, "2500.00")).isEmpty());
    }

    @Test
    void reevaluateEarnedTier_afterCancellation_returnsEmpty() {
        service.subscribe("USER_1", CatalogSeeder.MONTHLY_PLAN_ID, TierLevel.SILVER, "CREDIT_CARD");
        service.cancelSubscription("USER_1");

        assertFalse(service.reevaluateEarnedTier(metrics(6, "2500.00")).isPresent());
    }

    @Test
    void reevaluateEarnedTiers_batchUpdatesEverySubscribedUser_andSkipsUnsubscribedOnes() {
        service.subscribe("USER_A", CatalogSeeder.MONTHLY_PLAN_ID, TierLevel.SILVER, "CREDIT_CARD");
        service.subscribe("USER_B", CatalogSeeder.MONTHLY_PLAN_ID, TierLevel.SILVER, "CREDIT_CARD");

        service.reevaluateEarnedTiers(List.of(
                new UserMetrics("USER_A", 6, BigDecimal.ZERO, Set.of()),
                new UserMetrics("USER_B", 0, BigDecimal.ZERO, Set.of("VIP_CLUB")),
                new UserMetrics("USER_NOT_SUBSCRIBED", 50, BigDecimal.ZERO, Set.of())));

        assertEquals(TierLevel.GOLD, service.getSubscription("USER_A").orElseThrow().getEffectiveTier());
        assertEquals(TierLevel.PLATINUM, service.getSubscription("USER_B").orElseThrow().getEffectiveTier());
        assertTrue(service.getSubscription("USER_NOT_SUBSCRIBED").isEmpty());
    }

    @Test
    void evaluateBenefits_followEarnedTierWhenItExceedsPurchasedTier() {
        service.subscribe("USER_1", CatalogSeeder.MONTHLY_PLAN_ID, TierLevel.SILVER, "CREDIT_CARD");
        service.reevaluateEarnedTier(metrics(0, "0.00", "VIP_CLUB")); // earned Platinum

        OrderBenefitsResult result = service.evaluateBenefits("USER_1", order("1000.00", "GROCERY", "40.00", false, true));

        assertEquals(new BigDecimal("200.00"), result.getDiscountAmount(), "Platinum's 20%, not Silver's 5%");
        assertTrue(result.isPrioritySupportGranted());
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
