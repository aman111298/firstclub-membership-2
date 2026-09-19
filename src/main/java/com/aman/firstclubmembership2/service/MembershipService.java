package com.aman.firstclubmembership2.service;

import com.aman.firstclubmembership2.benefit.MembershipBenefit;
import com.aman.firstclubmembership2.domain.MembershipPlan;
import com.aman.firstclubmembership2.domain.MembershipTier;
import com.aman.firstclubmembership2.domain.UserSubscription;
import com.aman.firstclubmembership2.enums.SubscriptionStatus;
import com.aman.firstclubmembership2.enums.TierLevel;
import com.aman.firstclubmembership2.exception.PaymentFailedException;
import com.aman.firstclubmembership2.model.OrderBenefitsResult;
import com.aman.firstclubmembership2.model.OrderContext;
import com.aman.firstclubmembership2.model.PaymentContext;
import com.aman.firstclubmembership2.model.UserMetrics;
import com.aman.firstclubmembership2.repository.PaymentLogRepository;
import com.aman.firstclubmembership2.repository.PlanRepository;
import com.aman.firstclubmembership2.repository.SubscriptionRepository;
import com.aman.firstclubmembership2.repository.TierRepository;
import com.aman.firstclubmembership2.store.IdGenerator;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class MembershipService {

    private static final String PAYMENT_SUCCESS = "SUCCESS";
    private static final String PAYMENT_FAILED = "FAILED";

    private final PlanRepository planRepository;
    private final TierRepository tierRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final PaymentLogRepository paymentLogRepository;
    private final IdGenerator idGenerator;

    public MembershipService(PlanRepository planRepository,
                              TierRepository tierRepository,
                              SubscriptionRepository subscriptionRepository,
                              PaymentLogRepository paymentLogRepository,
                              IdGenerator idGenerator) {
        this.planRepository = planRepository;
        this.tierRepository = tierRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.paymentLogRepository = paymentLogRepository;
        this.idGenerator = idGenerator;
    }

    /** Returns every plan and tier in the catalog, for the user to choose a plan + tier from. */
    public Map<String, Object> getPlansAndTiers() {
        Map<String, Object> result = new HashMap<>();
        result.put("plans", planRepository.findAll());
        result.put("tiers", tierRepository.findAll());
        return result;
    }

    public UserSubscription subscribe(String userId, String planId, TierLevel tierLevel, String paymentMethod) {
        // Validate the plan exists
        MembershipPlan plan = planRepository.findById(planId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid Plan ID: " + planId));

        // Validate the tier is one the catalog actually offers
        if (tierRepository.findByLevel(tierLevel).isEmpty()) {
            throw new IllegalArgumentException("Invalid Tier Level: " + tierLevel);
        }

        // A user may only have one usable subscription at a time; a past (expired/cancelled)
        // one does not block a new subscribe call
        Optional<UserSubscription> existingSub = subscriptionRepository.findActiveByUserId(userId);
        if (existingSub.isPresent() && !existingSub.get().isExpired()) {
            throw new IllegalStateException("User already has an active subscription: " + existingSub.get().getSubscriptionId());
        }

        // Execute payment
        PaymentContext paymentContext = processPayment(userId, plan.getPrice(), paymentMethod, "Subscribe to " + plan.getName());

        // Guard: payment must succeed before the subscription is created
        if (!PAYMENT_SUCCESS.equalsIgnoreCase(paymentContext.status())) {
            throw new PaymentFailedException("Subscription failed: Payment processing failed for user " + userId);
        }

        // Create and register the subscription
        String subId = idGenerator.nextSubscriptionId();
        UserSubscription subscription = new UserSubscription(
                subId,
                userId,
                planId,
                tierLevel,
                plan.getBillingCycle().getDays(), // duration of the subscription: 30/90/365 days
                paymentContext
        );

        // Registers as both the user's current subscription and a permanent history entry
        subscriptionRepository.save(subscription);

        return subscription;
    }

    /** Mock payment execution: any non-negative amount succeeds. Also writes the audit log entry. */
    private PaymentContext processPayment(String userId, BigDecimal amount, String paymentMethod, String description) {
        String paymentId = idGenerator.nextPaymentLogId();

        // Mock gateway: a negative amount is the only way to simulate a failed charge
        boolean isPaymentSuccessful = amount.compareTo(BigDecimal.ZERO) >= 0;
        String status = isPaymentSuccessful ? PAYMENT_SUCCESS : PAYMENT_FAILED;

        PaymentContext paymentContext = new PaymentContext(
                paymentId,
                amount,
                status,
                paymentMethod,
                LocalDateTime.now()
        );

        // Keep a permanent audit record regardless of success/failure
        paymentLogRepository.save(paymentContext);

        System.out.printf("[PAYMENT LOG] TxnID: %s | User: %s | Amount: $%s | Status: %s | Method: %s | Note: %s%n",
                paymentId, userId, amount, status, paymentMethod, description);

        return paymentContext;
    }

    public TierLevel evaluateEligibleTier(UserMetrics metrics) {
        // Among all tiers whose rules these metrics satisfy, pick the highest-ranked one
        // (e.g. a user who qualifies for both Gold and Platinum lands on Platinum).
        // No tier's rules pass -> fall back to Silver, the baseline tier.
        return tierRepository.findAll().stream()
                .filter(tier -> tier.qualifies(metrics))
                .map(MembershipTier::getLevel)
                .max(Comparator.comparingInt(TierLevel::getRank))
                .orElse(TierLevel.SILVER);
    }

    /**
     * Automatic tier movement: re-evaluates a user's current subscription against fresh
     * metrics (e.g. after a new order) and moves the tier up or down if it no longer
     * matches what the user qualifies for.
     */
    public Optional<UserSubscription> evaluateAndUpdateUserTier(UserMetrics metrics) {
        Optional<UserSubscription> maybeSub = subscriptionRepository.findActiveByUserId(metrics.userId());

        // Nothing to re-evaluate if the user isn't currently subscribed
        if (maybeSub.isEmpty() || maybeSub.get().isExpired()) {
            System.out.printf("[TIER EVALUATION] Skip evaluation: No active subscription for user %s%n", metrics.userId());
            return Optional.empty();
        }

        UserSubscription sub = maybeSub.get();
        TierLevel currentTier = sub.getTierLevel();
        TierLevel qualifiedTier = evaluateEligibleTier(metrics);

        if (currentTier != qualifiedTier) {
            // Rank comparison tells us whether this move is an upgrade or a downgrade
            String transitionType = qualifiedTier.getRank() > currentTier.getRank() ? "UPGRADE" : "DOWNGRADE";

            sub.updateTier(qualifiedTier);

            System.out.printf("[TIER CHANGE LOG] User: %s | Action: %s | From: %s -> To: %s | Reason: Orders=%d, Spend=$%s%n",
                    metrics.userId(), transitionType, currentTier, qualifiedTier,
                    metrics.monthlyOrderCount(), metrics.monthlyOrderValue());
        } else {
            // Still qualifies for the same tier - nothing changes, just log it
            System.out.printf("[TIER EVALUATION] User: %s | Retained Tier: %s%n", metrics.userId(), currentTier);
        }

        return Optional.of(sub);
    }

    /** Manual tier change requested directly by the user (not driven by metrics evaluation). */
    public UserSubscription changeTierManual(String userId, TierLevel newTier) {
        UserSubscription sub = subscriptionRepository.findActiveByUserId(userId)
                .filter(s -> !s.isExpired())
                .orElseThrow(() -> new IllegalStateException("No active subscription found for user: " + userId));

        TierLevel oldTier = sub.getTierLevel();
        if (oldTier == newTier) {
            // Already at the requested tier - nothing to do
            return sub;
        }

        String action = newTier.getRank() > oldTier.getRank() ? "UPGRADE" : "DOWNGRADE";
        System.out.printf("[MANUAL TIER LOG] User: %s | %s from %s to %s%n", userId, action, oldTier, newTier);

        sub.updateTier(newTier);
        return sub;
    }

    /** Looks up the user's current subscription, lazily marking it EXPIRED if its end date has passed. */
    public Optional<UserSubscription> getSubscription(String userId) {
        Optional<UserSubscription> maybeSub = subscriptionRepository.findActiveByUserId(userId);
        if (maybeSub.isEmpty()) {
            return Optional.empty();
        }

        UserSubscription sub = maybeSub.get();

        // Lazy expiry: nothing runs on a timer, so any read is where we detect
        // and record that the subscription's period has ended
        if (LocalDateTime.now().isAfter(sub.getEndDate()) && sub.getStatus() == SubscriptionStatus.ACTIVE) {
            sub.setStatus(SubscriptionStatus.EXPIRED);
        }

        return Optional.of(sub);
    }

    public void cancelSubscription(String userId) {
        UserSubscription sub = subscriptionRepository.findActiveByUserId(userId)
                .orElseThrow(() -> new IllegalStateException("No subscription found for user: " + userId));

        // Marks the subscription CANCELLED; endDate is left untouched
        sub.cancel();
        System.out.printf("[SUBSCRIPTION LOG] Cancelled subscription %s for user %s%n", sub.getSubscriptionId(), userId);
    }

    // ---------------------------------------------------------------
    // Membership Benefits: configurable catalog + evaluation
    // ---------------------------------------------------------------

    /** Returns the configured benefit strategies for the user's current tier, or an empty list if not subscribed. */
    public List<MembershipBenefit> getBenefits(String userId) {
        return resolveBenefits(userId);
    }

    /**
     * Runs every benefit the user's tier grants against one order context, accumulating their
     * combined effect (discount, delivery fee, entitlement flags) into a single result.
     */
    public OrderBenefitsResult evaluateBenefits(String userId, OrderContext context) {
        OrderBenefitsResult result = new OrderBenefitsResult(context.standardDeliveryFee());
        resolveBenefits(userId).forEach(benefit -> benefit.apply(context, result));
        return result;
    }

    private List<MembershipBenefit> resolveBenefits(String userId) {
        return subscriptionRepository.findActiveByUserId(userId)
                .filter(sub -> !sub.isExpired())
                .flatMap(sub -> tierRepository.findByLevel(sub.getTierLevel()))
                .map(MembershipTier::getBenefits)
                .orElse(List.of());
    }
}
