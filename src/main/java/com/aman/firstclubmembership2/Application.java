package com.aman.firstclubmembership2;

import com.aman.firstclubmembership2.concurrency.UserLockManager;
import com.aman.firstclubmembership2.config.CatalogSeeder;
import com.aman.firstclubmembership2.domain.MembershipPlan;
import com.aman.firstclubmembership2.domain.MembershipTier;
import com.aman.firstclubmembership2.domain.UserSubscription;
import com.aman.firstclubmembership2.enums.TierLevel;
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
import com.aman.firstclubmembership2.service.MembershipService;
import com.aman.firstclubmembership2.store.IdGenerator;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * Scripted walkthrough of every scenario in the README's problem statement (section 1):
 * Membership Plans, Membership Tiers, User Actions, and Membership Benefits.
 */
public class Application {

    public static void main(String[] args) {
        IdGenerator idGenerator = new IdGenerator();
        PlanRepository planRepository = new InMemoryPlanRepository();
        TierRepository tierRepository = new InMemoryTierRepository();
        SubscriptionRepository subscriptionRepository = new InMemorySubscriptionRepository();
        PaymentLogRepository paymentLogRepository = new InMemoryPaymentLogRepository();
        UserLockManager userLockManager = new UserLockManager();

        CatalogSeeder.seed(planRepository, tierRepository, idGenerator);

        MembershipService service = new MembershipService(
                planRepository, tierRepository, subscriptionRepository, paymentLogRepository, idGenerator, userLockManager);

        // Requirement: "Get Membership Plans and Tier to be selected by the user."
        section("1. MEMBERSHIP PLANS & TIERS - BROWSE THE CATALOG");
        printCatalog(service);

        // Requirement: "Users move through tiers ... based on criteria" - a brand-new user
        // with low activity is evaluated against every tier's criteria and lands on the
        // baseline tier (Silver has no rules, so it always qualifies).
        section("2. MEMBERSHIP TIERS - EVALUATE ELIGIBLE TIER FROM CRITERIA (NEW USER)");
        UserMetrics initialMetrics = new UserMetrics("USER_101", 2, new BigDecimal("300.00"), Set.of("REGULAR"));
        TierLevel initialTier = service.evaluateEligibleTier(initialMetrics);
        System.out.println("USER_101 currently qualifies for: " + initialTier);

        // Requirement: "Subscribe to a plan (plan + tier)."
        section("3. USER ACTIONS - SUBSCRIBE TO A PLAN + TIER");
        UserSubscription sub = service.subscribe("USER_101", CatalogSeeder.MONTHLY_PLAN_ID, initialTier, "CREDIT_CARD");
        System.out.println("Active Sub ID: " + sub.getSubscriptionId() + " | Payment ID: " + sub.getPaymentContext().paymentId());

        // Requirement: "Users move through tiers based on ... Number of Order more than X ...
        // Total Order value in a month" - automatic, metrics-driven tier movement.
        section("4. MEMBERSHIP TIERS - AUTOMATIC UPGRADE VIA ORDER COUNT & SPEND (SILVER -> GOLD)");
        UserMetrics updatedMetricsGold = new UserMetrics("USER_101", 6, new BigDecimal("2500.00"), Set.of("REGULAR"));
        service.evaluateAndUpdateUserTier(updatedMetricsGold);

        // Requirement: "Users move through tiers based on ... User belonging to a certain cohort."
        section("5. MEMBERSHIP TIERS - AUTOMATIC UPGRADE VIA COHORT TAG (GOLD -> PLATINUM)");
        UserMetrics updatedMetricsPlatinum = new UserMetrics("USER_101", 6, new BigDecimal("2500.00"), Set.of("VIP_CLUB"));
        service.evaluateAndUpdateUserTier(updatedMetricsPlatinum);

        // Requirement: "Track current membership and expiry."
        section("6. USER ACTIONS - TRACK CURRENT MEMBERSHIP AND EXPIRY");
        service.getSubscription("USER_101").ifPresent(userSub -> {
            System.out.println("User ID: " + userSub.getUserId());
            System.out.println("Current Tier: " + userSub.getTierLevel());
            System.out.println("Status: " + userSub.getStatus());
            System.out.println("End Date: " + userSub.getEndDate());
        });

        // Requirement: "Upgrade, downgrade (Membership Tier) ... a subscription" - a direct,
        // user-requested tier change, independent of the automatic criteria-based evaluation above.
        section("7. USER ACTIONS - MANUAL DOWNGRADE, THEN MANUAL UPGRADE");
        UserSubscription downgraded = service.changeTierManual("USER_101", TierLevel.GOLD);
        System.out.println("Manually downgraded to: " + downgraded.getTierLevel());
        UserSubscription upgraded = service.changeTierManual("USER_101", TierLevel.PLATINUM);
        System.out.println("Manually upgraded back to: " + upgraded.getTierLevel());

        // Requirement: "Membership Benefits - Free delivery on eligible orders. Extra X%
        // discount on selected items or categories. Access to exclusive deals and early
        // access to sales. Priority support for premium members."
        section("8. MEMBERSHIP BENEFITS - CONFIGURED BENEFITS FOR CURRENT TIER (PLATINUM)");
        System.out.println("Configured benefits: " + service.getBenefits("USER_101"));
        OrderContext platinumOrder = new OrderContext(new BigDecimal("50.00"), "ELECTRONICS", new BigDecimal("40.00"), true, true);
        OrderBenefitsResult platinumResult = service.evaluateBenefits("USER_101", platinumOrder);
        System.out.println("Order: $50.00 subtotal, ELECTRONICS, exclusive deal, priority support requested");
        System.out.println("Discount amount: $" + platinumResult.getDiscountAmount());
        System.out.println("Delivery fee: $" + platinumResult.getDeliveryFee());
        System.out.println("Early access granted: " + platinumResult.isEarlyAccessGranted());
        System.out.println("Priority support granted: " + platinumResult.isPrioritySupportGranted());

        // Requirement: "Each tier unlocks additional perks ... should be configurable" - the
        // SAME order shape evaluated for a Gold user shows fewer/different benefits: the
        // discount doesn't apply outside Gold's configured categories, the order is below
        // Gold's free-delivery threshold, and Gold has no priority-support benefit at all.
        section("9. MEMBERSHIP BENEFITS - SAME ORDER SHAPE, DIFFERENT TIER (GOLD)");
        service.subscribe("USER_202", CatalogSeeder.QUARTERLY_PLAN_ID, TierLevel.GOLD, "CREDIT_CARD");
        OrderContext goldOrder = new OrderContext(new BigDecimal("100.00"), "GROCERY", new BigDecimal("40.00"), true, true);
        OrderBenefitsResult goldResult = service.evaluateBenefits("USER_202", goldOrder);
        System.out.println("Gold user, GROCERY order $100.00 (below Gold's $499 free-delivery threshold):");
        System.out.println("Discount amount: $" + goldResult.getDiscountAmount() + " (GROCERY is not one of Gold's discount categories)");
        System.out.println("Delivery fee: $" + goldResult.getDeliveryFee() + " (threshold not met)");
        System.out.println("Early access granted: " + goldResult.isEarlyAccessGranted());
        System.out.println("Priority support granted: " + goldResult.isPrioritySupportGranted() + " (Gold has no priority-support benefit)");

        // Requirement: "Users can choose from Monthly, Quarterly, and Yearly membership plans."
        section("10. MEMBERSHIP PLANS - SUBSCRIBE TO A DIFFERENT BILLING CYCLE");
        UserSubscription yearlySub = service.subscribe("USER_303", CatalogSeeder.YEARLY_PLAN_ID, TierLevel.SILVER, "CREDIT_CARD");
        System.out.println("USER_303 subscribed to the Yearly plan, ends: " + yearlySub.getEndDate());

        // Requirement: "cancel a subscription."
        section("11. USER ACTIONS - CANCEL SUBSCRIPTION");
        service.cancelSubscription("USER_101");
        System.out.println("Is Expired/Cancelled: " + service.getSubscription("USER_101").get().isExpired());
    }

    @SuppressWarnings("unchecked")
    private static void printCatalog(MembershipService service) {
        Map<String, Object> catalog = service.getPlansAndTiers();

        Collection<MembershipPlan> plans = (Collection<MembershipPlan>) catalog.get("plans");
        System.out.println("Plans:");
        plans.forEach(plan -> System.out.println("  " + plan.getName() + " (" + plan.getBillingCycle() + ") - $" + plan.getPrice()));

        Collection<MembershipTier> tiers = (Collection<MembershipTier>) catalog.get("tiers");
        System.out.println("Tiers:");
        tiers.forEach(tier -> System.out.println("  " + tier.getName() + " [" + tier.getLevel() + "] - benefits: " + tier.getBenefits()));
    }

    private static void section(String title) {
        System.out.println();
        System.out.println("==========================================");
        System.out.println(title);
        System.out.println("==========================================");
    }
}
