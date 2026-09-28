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
import com.aman.firstclubmembership2.pricing.MultiplierTierPricingStrategy;
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
import java.util.List;
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
                planRepository, tierRepository, subscriptionRepository, paymentLogRepository, idGenerator, userLockManager,
                new MultiplierTierPricingStrategy());

        // Requirement: "Get Membership Plans and Tier to be selected by the user."
        section("1. MEMBERSHIP PLANS & TIERS - BROWSE THE CATALOG (PRICE = PLAN x TIER MULTIPLIER)");
        printCatalog(service);

        // Requirement: "Subscribe to a plan (plan + tier)." - the user pays for exactly the tier
        // they choose; activity criteria are NOT evaluated at purchase time.
        section("2. USER ACTIONS - BUY A PLAN + TIER (MONTHLY + SILVER)");
        UserSubscription sub = service.subscribe("USER_101", CatalogSeeder.MONTHLY_PLAN_ID, TierLevel.SILVER, "CREDIT_CARD");
        System.out.println("Active Sub ID: " + sub.getSubscriptionId() + " | Payment ID: " + sub.getPaymentContext().paymentId());
        printTiers(sub);

        // Requirement: "Users move through tiers based on ... Number of Order more than X ...
        // Total Order value in a month" - the scheduled job / order-event consumer re-evaluates
        // activity and raises the earned tier above what was bought, at no charge.
        section("3. MEMBERSHIP TIERS - CRON RE-EVALUATION VIA ORDER COUNT & SPEND (SILVER -> GOLD)");
        service.reevaluateEarnedTier(new UserMetrics("USER_101", 6, new BigDecimal("2500.00"), Set.of("REGULAR")));
        printTiers(sub);

        // Requirement: "Users move through tiers based on ... User belonging to a certain cohort."
        section("4. MEMBERSHIP TIERS - CRON RE-EVALUATION VIA COHORT TAG (GOLD -> PLATINUM)");
        service.reevaluateEarnedTier(new UserMetrics("USER_101", 6, new BigDecimal("2500.00"), Set.of("VIP_CLUB")));
        printTiers(sub);

        // Activity drops: the system never downgrades - only an admin can lower a tier.
        section("5. MEMBERSHIP TIERS - ACTIVITY DROPS, TIER IS RETAINED (NO AUTOMATIC DOWNGRADE)");
        service.reevaluateEarnedTier(new UserMetrics("USER_101", 1, new BigDecimal("100.00"), Set.of("REGULAR")));
        printTiers(sub);

        // Requirement: "Upgrade ... (Membership Tier)" - a user-initiated, paid upgrade, charged the
        // prorated price difference for the rest of the billing period.
        section("6. USER ACTIONS - PAID UPGRADE OF PURCHASED TIER (SILVER -> GOLD, PRORATED)");
        service.upgradeTier("USER_101", TierLevel.GOLD, "CREDIT_CARD");
        printTiers(sub);

        // Requirement: "Track current membership and expiry."
        section("7. USER ACTIONS - TRACK CURRENT MEMBERSHIP AND EXPIRY");
        service.getSubscription("USER_101").ifPresent(userSub -> {
            System.out.println("User ID: " + userSub.getUserId());
            System.out.println("Current (effective) Tier: " + userSub.getEffectiveTier());
            System.out.println("Status: " + userSub.getStatus());
            System.out.println("End Date: " + userSub.getEndDate());
        });

        // Requirement: "... downgrade (Membership Tier)" - admin-only, used to penalize a user. Lowers
        // the purchased tier and clears the earned one, so the effective tier drops immediately.
        section("8. ADMIN ACTIONS - DOWNGRADE AS A PENALTY (PLATINUM -> SILVER)");
        service.adminDowngradeTier("USER_101", TierLevel.SILVER);
        printTiers(sub);

        // Requirement: "Membership Benefits - Free delivery on eligible orders. Extra X%
        // discount on selected items or categories. Access to exclusive deals and early
        // access to sales. Priority support for premium members."
        section("9. MEMBERSHIP BENEFITS - CONFIGURED BENEFITS FOR A PURCHASED PLATINUM TIER");
        service.subscribe("USER_404", CatalogSeeder.MONTHLY_PLAN_ID, TierLevel.PLATINUM, "CREDIT_CARD");
        System.out.println("Configured benefits: " + service.getBenefits("USER_404"));
        OrderContext platinumOrder = new OrderContext(new BigDecimal("50.00"), "ELECTRONICS", new BigDecimal("40.00"), true, true);
        OrderBenefitsResult platinumResult = service.evaluateBenefits("USER_404", platinumOrder);
        System.out.println("Order: $50.00 subtotal, ELECTRONICS, exclusive deal, priority support requested");
        System.out.println("Discount amount: $" + platinumResult.getDiscountAmount());
        System.out.println("Delivery fee: $" + platinumResult.getDeliveryFee());
        System.out.println("Early access granted: " + platinumResult.isEarlyAccessGranted());
        System.out.println("Priority support granted: " + platinumResult.isPrioritySupportGranted());

        // Requirement: "Each tier unlocks additional perks ... should be configurable" - the
        // SAME order shape evaluated for a Gold user shows fewer/different benefits: the
        // discount doesn't apply outside Gold's configured categories, the order is below
        // Gold's free-delivery threshold, and Gold has no priority-support benefit at all.
        section("10. MEMBERSHIP BENEFITS - SAME ORDER SHAPE, DIFFERENT TIER (GOLD)");
        service.subscribe("USER_202", CatalogSeeder.QUARTERLY_PLAN_ID, TierLevel.GOLD, "CREDIT_CARD");
        OrderContext goldOrder = new OrderContext(new BigDecimal("100.00"), "GROCERY", new BigDecimal("40.00"), true, true);
        OrderBenefitsResult goldResult = service.evaluateBenefits("USER_202", goldOrder);
        System.out.println("Gold user, GROCERY order $100.00 (below Gold's $499 free-delivery threshold):");
        System.out.println("Discount amount: $" + goldResult.getDiscountAmount() + " (GROCERY is not one of Gold's discount categories)");
        System.out.println("Delivery fee: $" + goldResult.getDeliveryFee() + " (threshold not met)");
        System.out.println("Early access granted: " + goldResult.isEarlyAccessGranted());
        System.out.println("Priority support granted: " + goldResult.isPrioritySupportGranted() + " (Gold has no priority-support benefit)");

        // Requirement: "Users can choose from Monthly, Quarterly, and Yearly membership plans."
        section("11. MEMBERSHIP PLANS - SUBSCRIBE TO A DIFFERENT BILLING CYCLE");
        UserSubscription yearlySub = service.subscribe("USER_303", CatalogSeeder.YEARLY_PLAN_ID, TierLevel.SILVER, "CREDIT_CARD");
        System.out.println("USER_303 subscribed to the Yearly plan, ends: " + yearlySub.getEndDate());

        // The scheduled job's batch entry point: re-evaluates every user's earned tier in one run.
        section("12. CRON JOB - BATCH RE-EVALUATION OF EARNED TIERS");
        service.reevaluateEarnedTiers(List.of(
                new UserMetrics("USER_202", 20, new BigDecimal("5000.00"), Set.of()),
                new UserMetrics("USER_303", 5, new BigDecimal("800.00"), Set.of()),
                new UserMetrics("USER_999", 50, new BigDecimal("9000.00"), Set.of()) // not subscribed - skipped
        ));

        // Requirement: "cancel a subscription."
        section("13. USER ACTIONS - CANCEL SUBSCRIPTION");
        service.cancelSubscription("USER_101");
        System.out.println("Is Expired/Cancelled: " + service.getSubscription("USER_101").get().isExpired());
    }

    @SuppressWarnings("unchecked")
    private static void printCatalog(MembershipService service) {
        Map<String, Object> catalog = service.getPlansAndTiers();

        Collection<MembershipPlan> plans = (Collection<MembershipPlan>) catalog.get("plans");
        System.out.println("Plans:");
        plans.forEach(plan -> System.out.println("  " + plan.getName() + " (" + plan.getBillingCycle() + ") - base $" + plan.getPrice()));

        Collection<MembershipTier> tiers = (Collection<MembershipTier>) catalog.get("tiers");
        System.out.println("Tiers:");
        tiers.forEach(tier -> System.out.println("  " + tier.getName() + " [" + tier.getLevel() + "] x"
                + tier.getPriceMultiplier() + " - benefits: " + tier.getBenefits()));

        System.out.println("Price to buy (plan + tier):");
        plans.forEach(plan -> tiers.forEach(tier -> System.out.println("  " + plan.getName() + " + " + tier.getLevel()
                + " = $" + service.getPrice(plan.getPlanId(), tier.getLevel()))));
    }

    private static void printTiers(UserSubscription sub) {
        System.out.println("Purchased: " + sub.getPurchasedTier() + " | Earned: " + sub.getEarnedTier()
                + " | Effective (benefits): " + sub.getEffectiveTier());
    }

    private static void section(String title) {
        System.out.println();
        System.out.println("==========================================");
        System.out.println(title);
        System.out.println("==========================================");
    }
}
