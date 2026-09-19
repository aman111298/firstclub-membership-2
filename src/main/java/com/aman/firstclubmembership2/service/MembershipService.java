package com.aman.firstclubmembership2.service;

import com.aman.firstclubmembership2.domain.MembershipPlan;
import com.aman.firstclubmembership2.domain.MembershipTier;
import com.aman.firstclubmembership2.domain.UserSubscription;
import com.aman.firstclubmembership2.enums.SubscriptionStatus;
import com.aman.firstclubmembership2.enums.TierLevel;
import com.aman.firstclubmembership2.exception.PaymentFailedException;
import com.aman.firstclubmembership2.model.PaymentContext;
import com.aman.firstclubmembership2.model.UserMetrics;
import com.aman.firstclubmembership2.store.DataStore;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class MembershipService {

    private static final String PAYMENT_SUCCESS = "SUCCESS";
    private static final String PAYMENT_FAILED = "FAILED";

    private final DataStore dataStore;

    public MembershipService(DataStore dataStore) {
        this.dataStore = dataStore;
    }

    public Map<String, Object> getPlansAndTiers() {
        Map<String, Object> result = new HashMap<>();
        result.put("plans", dataStore.plans.values());
        result.put("tiers", dataStore.tiers.values());
        return result;
    }

    public UserSubscription subscribe(String userId, String planId, TierLevel tierLevel, String paymentMethod) {
        MembershipPlan plan = dataStore.plans.get(planId);
        if (plan == null) {
            throw new IllegalArgumentException("Invalid Plan ID: " + planId);
        }

        if (!dataStore.tiers.containsKey(tierLevel.name())) {
            throw new IllegalArgumentException("Invalid Tier Level: " + tierLevel);
        }

        UserSubscription existingSub = dataStore.activeSubscriptions.get(userId);
        if (existingSub != null && !existingSub.isExpired()) {
            throw new IllegalStateException("User already has an active subscription: " + existingSub.getSubscriptionId());
        }

        PaymentContext paymentContext = processPayment(userId, plan.getPrice(), paymentMethod, "Subscribe to " + plan.getName());

        if (!PAYMENT_SUCCESS.equalsIgnoreCase(paymentContext.status())) {
            throw new PaymentFailedException("Subscription failed: Payment processing failed for user " + userId);
        }

        String subId = dataStore.idGenerator.nextSubscriptionId();
        UserSubscription subscription = new UserSubscription(
                subId,
                userId,
                planId,
                tierLevel,
                plan.getBillingCycle().getDays(),
                paymentContext
        );

        dataStore.activeSubscriptions.put(userId, subscription);
        dataStore.subscriptionHistory.put(subId, subscription);

        return subscription;
    }

    /** Mock payment execution: any non-negative amount succeeds. Also writes the audit log entry. */
    private PaymentContext processPayment(String userId, BigDecimal amount, String paymentMethod, String description) {
        String paymentId = dataStore.idGenerator.nextPaymentLogId();

        boolean isPaymentSuccessful = amount.compareTo(BigDecimal.ZERO) >= 0;
        String status = isPaymentSuccessful ? PAYMENT_SUCCESS : PAYMENT_FAILED;

        PaymentContext paymentContext = new PaymentContext(
                paymentId,
                amount,
                status,
                paymentMethod,
                LocalDateTime.now()
        );

        dataStore.paymentLogs.put(paymentId, paymentContext);

        System.out.printf("[PAYMENT LOG] TxnID: %s | User: %s | Amount: $%s | Status: %s | Method: %s | Note: %s%n",
                paymentId, userId, amount, status, paymentMethod, description);

        return paymentContext;
    }

    public TierLevel evaluateEligibleTier(UserMetrics metrics) {
        return dataStore.tiers.values().stream()
                .filter(tier -> tier.qualifies(metrics))
                .map(MembershipTier::getLevel)
                .max(Comparator.comparingInt(TierLevel::getRank))
                .orElse(TierLevel.SILVER);
    }

    public Optional<UserSubscription> evaluateAndUpdateUserTier(UserMetrics metrics) {
        UserSubscription sub = dataStore.activeSubscriptions.get(metrics.userId());

        if (sub == null || sub.isExpired()) {
            System.out.printf("[TIER EVALUATION] Skip evaluation: No active subscription for user %s%n", metrics.userId());
            return Optional.empty();
        }

        TierLevel currentTier = sub.getTierLevel();
        TierLevel qualifiedTier = evaluateEligibleTier(metrics);

        if (currentTier != qualifiedTier) {
            String transitionType = qualifiedTier.getRank() > currentTier.getRank() ? "UPGRADE" : "DOWNGRADE";

            sub.updateTier(qualifiedTier);

            System.out.printf("[TIER CHANGE LOG] User: %s | Action: %s | From: %s -> To: %s | Reason: Orders=%d, Spend=$%s%n",
                    metrics.userId(), transitionType, currentTier, qualifiedTier,
                    metrics.monthlyOrderCount(), metrics.monthlyOrderValue());
        } else {
            System.out.printf("[TIER EVALUATION] User: %s | Retained Tier: %s%n", metrics.userId(), currentTier);
        }

        return Optional.of(sub);
    }

    public UserSubscription changeTierManual(String userId, TierLevel newTier) {
        UserSubscription sub = dataStore.activeSubscriptions.get(userId);
        if (sub == null || sub.isExpired()) {
            throw new IllegalStateException("No active subscription found for user: " + userId);
        }

        TierLevel oldTier = sub.getTierLevel();
        if (oldTier == newTier) {
            return sub;
        }

        String action = newTier.getRank() > oldTier.getRank() ? "UPGRADE" : "DOWNGRADE";
        System.out.printf("[MANUAL TIER LOG] User: %s | %s from %s to %s%n", userId, action, oldTier, newTier);

        sub.updateTier(newTier);
        return sub;
    }

    public Optional<UserSubscription> getSubscription(String userId) {
        UserSubscription sub = dataStore.activeSubscriptions.get(userId);
        if (sub == null) {
            return Optional.empty();
        }

        if (LocalDateTime.now().isAfter(sub.getEndDate()) && sub.getStatus() == SubscriptionStatus.ACTIVE) {
            sub.setStatus(SubscriptionStatus.EXPIRED);
        }

        return Optional.of(sub);
    }

    public void cancelSubscription(String userId) {
        UserSubscription sub = dataStore.activeSubscriptions.get(userId);
        if (sub == null) {
            throw new IllegalStateException("No subscription found for user: " + userId);
        }

        sub.cancel();
        System.out.printf("[SUBSCRIPTION LOG] Cancelled subscription %s for user %s%n", sub.getSubscriptionId(), userId);
    }
}
