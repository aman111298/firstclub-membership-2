package com.aman.firstclubmembership2;

import com.aman.firstclubmembership2.config.CatalogSeeder;
import com.aman.firstclubmembership2.domain.UserSubscription;
import com.aman.firstclubmembership2.enums.TierLevel;
import com.aman.firstclubmembership2.model.UserMetrics;
import com.aman.firstclubmembership2.service.MembershipService;
import com.aman.firstclubmembership2.store.DataStore;

import java.math.BigDecimal;
import java.util.Set;

public class Application {

    public static void main(String[] args) {
        DataStore dataStore = new DataStore();
        MembershipService service = new MembershipService(dataStore);

        CatalogSeeder.seed(dataStore);

        section("1. USER INITIAL SUBSCRIPTION");
        UserMetrics initialMetrics = new UserMetrics("USER_101", 2, new BigDecimal("300.00"), Set.of("REGULAR"));
        TierLevel initialTier = service.evaluateEligibleTier(initialMetrics);

        UserSubscription sub = service.subscribe("USER_101", CatalogSeeder.MONTHLY_PLAN_ID, initialTier, "CREDIT_CARD");
        System.out.println("Active Sub ID: " + sub.getSubscriptionId() + " | Payment ID: " + sub.getPaymentContext().paymentId());

        section("2. AUTOMATIC UPGRADE TO GOLD (VIA SPEND & ORDERS)");
        UserMetrics updatedMetricsGold = new UserMetrics("USER_101", 6, new BigDecimal("2500.00"), Set.of("REGULAR"));
        service.evaluateAndUpdateUserTier(updatedMetricsGold);

        section("3. AUTOMATIC UPGRADE TO PLATINUM (VIA COHORT TAG)");
        UserMetrics updatedMetricsPlatinum = new UserMetrics("USER_101", 6, new BigDecimal("2500.00"), Set.of("VIP_CLUB"));
        service.evaluateAndUpdateUserTier(updatedMetricsPlatinum);

        section("4. TRACK CURRENT MEMBERSHIP DETAILS");
        service.getSubscription("USER_101").ifPresent(userSub -> {
            System.out.println("User ID: " + userSub.getUserId());
            System.out.println("Current Tier: " + userSub.getTierLevel());
            System.out.println("Status: " + userSub.getStatus());
            System.out.println("End Date: " + userSub.getEndDate());
        });

        section("5. CANCEL SUBSCRIPTION");
        service.cancelSubscription("USER_101");
        System.out.println("Is Expired/Cancelled: " + service.getSubscription("USER_101").get().isExpired());
    }

    private static void section(String title) {
        System.out.println();
        System.out.println("==========================================");
        System.out.println(title);
        System.out.println("==========================================");
    }
}
