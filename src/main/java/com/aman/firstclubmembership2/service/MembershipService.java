package com.aman.firstclubmembership2.service;

import com.aman.firstclubmembership2.benefit.MembershipBenefit;
import com.aman.firstclubmembership2.concurrency.UserLockManager;
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
import com.aman.firstclubmembership2.pricing.TierPricingStrategy;
import com.aman.firstclubmembership2.repository.PaymentLogRepository;
import com.aman.firstclubmembership2.repository.PlanRepository;
import com.aman.firstclubmembership2.repository.SubscriptionRepository;
import com.aman.firstclubmembership2.repository.TierRepository;
import com.aman.firstclubmembership2.store.IdGenerator;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;

public class MembershipService {

    private static final String PAYMENT_SUCCESS = "SUCCESS";
    private static final String PAYMENT_FAILED = "FAILED";

    private final PlanRepository planRepository;
    private final TierRepository tierRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final PaymentLogRepository paymentLogRepository;
    private final IdGenerator idGenerator;
    private final UserLockManager userLockManager;
    private final TierPricingStrategy tierPricingStrategy;

    public MembershipService(PlanRepository planRepository,
                              TierRepository tierRepository,
                              SubscriptionRepository subscriptionRepository,
                              PaymentLogRepository paymentLogRepository,
                              IdGenerator idGenerator,
                              UserLockManager userLockManager,
                              TierPricingStrategy tierPricingStrategy) {
        this.planRepository = planRepository;
        this.tierRepository = tierRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.paymentLogRepository = paymentLogRepository;
        this.idGenerator = idGenerator;
        this.userLockManager = userLockManager;
        this.tierPricingStrategy = tierPricingStrategy;
    }

    /** Returns every plan and tier in the catalog, for the user to choose a plan + tier from. */
    public Map<String, Object> getPlansAndTiers() {
        Map<String, Object> result = new HashMap<>();
        result.put("plans", planRepository.findAll());
        result.put("tiers", tierRepository.findAll());
        return result;
    }

    /** Price of buying the given plan + tier, so the user can see it before subscribing. */
    public BigDecimal getPrice(String planId, TierLevel tierLevel) {
        return tierPricingStrategy.priceFor(findPlan(planId), findTier(tierLevel));
    }

    /**
     * Buys a plan + tier. The user is charged plan price x tier multiplier and gets exactly the
     * tier they paid for. Activity criteria are deliberately NOT evaluated here - earning a
     * higher tier happens later, through {@link #reevaluateEarnedTier(UserMetrics)}.
     */
    public UserSubscription subscribe(String userId, String planId, TierLevel tierLevel, String paymentMethod) {
        // The whole check-then-act sequence (existing-subscription check, payment, create + save)
        // must run under this user's lock, or two concurrent calls could both pass the check
        // and both charge/create a subscription before either writes.
        ReentrantLock lock = userLockManager.lockFor(userId);
        lock.lock();
        try {
            // Validate the plan exists and the tier is one the catalog actually offers
            MembershipPlan plan = findPlan(planId);
            MembershipTier tier = findTier(tierLevel);

            // A user may only have one usable subscription at a time; a past (expired/cancelled)
            // one does not block a new subscribe call
            Optional<UserSubscription> existingSub = subscriptionRepository.findActiveByUserId(userId);
            if (existingSub.isPresent() && !existingSub.get().isExpired()) {
                throw new IllegalStateException("User already has an active subscription: " + existingSub.get().getSubscriptionId());
            }

            // Execute payment for plan + tier
            BigDecimal price = tierPricingStrategy.priceFor(plan, tier);
            PaymentContext paymentContext = processPayment(userId, price, paymentMethod,
                    "Subscribe to " + plan.getName() + " + " + tier.getName());

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
        } finally {
            lock.unlock();
        }
    }

    /**
     * User-initiated, paid upgrade of the purchased tier. Charges the price difference between
     * the two tiers, prorated for the time left in the current billing period.
     *
     * Allowed even if the user has already earned the target tier: buying it locks it in as a
     * floor, so a drop in activity can no longer take it away.
     *
     * There is no user-facing downgrade - see {@link #adminDowngradeTier(String, TierLevel)}.
     */
    public UserSubscription upgradeTier(String userId, TierLevel newTier, String paymentMethod) {
        ReentrantLock lock = userLockManager.lockFor(userId);
        lock.lock();
        try {
            UserSubscription sub = findUsableSubscription(userId);

            TierLevel currentTier = sub.getPurchasedTier();
            if (newTier.getRank() <= currentTier.getRank()) {
                throw new IllegalArgumentException("Upgrade must be to a higher tier than the purchased tier "
                        + currentTier + ", got: " + newTier);
            }

            MembershipPlan plan = findPlan(sub.getPlanId());
            BigDecimal fullDifference = tierPricingStrategy.priceFor(plan, findTier(newTier))
                    .subtract(tierPricingStrategy.priceFor(plan, findTier(currentTier)));
            BigDecimal proratedAmount = prorateForRemainingPeriod(fullDifference, sub);

            PaymentContext paymentContext = processPayment(userId, proratedAmount, paymentMethod,
                    "Upgrade " + currentTier + " -> " + newTier + " (prorated)");

            // Guard: payment must succeed before the tier changes
            if (!PAYMENT_SUCCESS.equalsIgnoreCase(paymentContext.status())) {
                throw new PaymentFailedException("Upgrade failed: Payment processing failed for user " + userId);
            }

            sub.upgradePurchasedTier(newTier);
            System.out.printf("[PAID UPGRADE LOG] User: %s | %s -> %s | Charged: $%s%n",
                    userId, currentTier, newTier, proratedAmount);
            return sub;
        } finally {
            lock.unlock();
        }
    }

    /**
     * ADMIN ONLY - not exposed to end users. Used only when we want to penalize a user
     * (e.g. abuse, fraud, policy violation). Lowers the purchased tier immediately, with no refund,
     * and clears the earned tier so the effective tier drops right away. The next scheduled
     * re-evaluation may earn a higher tier back; a lasting penalty would need a hold/cap flag.
     *
     * Users cannot downgrade themselves: a tier they paid for stays until the period ends.
     */
    public UserSubscription adminDowngradeTier(String userId, TierLevel newTier) {
        ReentrantLock lock = userLockManager.lockFor(userId);
        lock.lock();
        try {
            UserSubscription sub = findUsableSubscription(userId);
            findTier(newTier);

            TierLevel currentTier = sub.getPurchasedTier();
            if (newTier.getRank() >= currentTier.getRank()) {
                throw new IllegalArgumentException("Downgrade must be to a lower tier than the purchased tier "
                        + currentTier + ", got: " + newTier);
            }

            sub.downgradeByAdmin(newTier);
            System.out.printf("[ADMIN DOWNGRADE LOG] User: %s | %s -> %s (penalty)%n", userId, currentTier, newTier);
            return sub;
        } finally {
            lock.unlock();
        }
    }

    /** Scales a full-period price difference down to the share of the billing period still remaining. */
    private BigDecimal prorateForRemainingPeriod(BigDecimal fullDifference, UserSubscription sub) {
        long totalSeconds = Duration.between(sub.getStartDate(), sub.getEndDate()).getSeconds();
        long remainingSeconds = Math.max(0, Duration.between(LocalDateTime.now(), sub.getEndDate()).getSeconds());
        return fullDifference
                .multiply(BigDecimal.valueOf(remainingSeconds))
                .divide(BigDecimal.valueOf(totalSeconds), 2, RoundingMode.HALF_UP);
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
     * SYSTEM-TRIGGERED - intended to be called by a scheduled (cron) job or an order-event consumer,
     * not by the user. Re-evaluates one user's activity and can only UPGRADE: if the criteria
     * qualify the user for a tier above their current effective tier, that becomes their earned
     * tier. It never downgrades - a drop in activity leaves the tier as is; lowering a tier is an
     * admin-only action ({@link #adminDowngradeTier(String, TierLevel)}). It also never charges and
     * never touches the purchased tier.
     */
    public Optional<UserSubscription> reevaluateEarnedTier(UserMetrics metrics) {
        // Locked so this doesn't race with a paid upgrade / admin downgrade (or another evaluation)
        // on the same user reading stale tier state and silently overwriting each other's result.
        ReentrantLock lock = userLockManager.lockFor(metrics.userId());
        lock.lock();
        try {
            Optional<UserSubscription> maybeSub = subscriptionRepository.findActiveByUserId(metrics.userId());

            // Nothing to re-evaluate if the user isn't currently subscribed
            if (maybeSub.isEmpty() || maybeSub.get().isExpired()) {
                System.out.printf("[TIER EVALUATION] Skip evaluation: No active subscription for user %s%n", metrics.userId());
                return Optional.empty();
            }

            UserSubscription sub = maybeSub.get();
            TierLevel currentTier = sub.getEffectiveTier();
            TierLevel qualifiedTier = evaluateEligibleTier(metrics);

            if (qualifiedTier.getRank() > currentTier.getRank()) {
                // Criteria qualify the user for a higher tier - upgrade by recording it as earned
                sub.setEarnedTier(qualifiedTier);
                System.out.printf("[TIER CHANGE LOG] User: %s | Action: UPGRADE | From: %s -> To: %s | Purchased: %s | Reason: Orders=%d, Spend=$%s%n",
                        metrics.userId(), currentTier, qualifiedTier, sub.getPurchasedTier(),
                        metrics.monthlyOrderCount(), metrics.monthlyOrderValue());
            } else {
                // Same or lower qualification - no change; only an admin can downgrade
                System.out.printf("[TIER EVALUATION] User: %s | Retained Tier: %s (criteria qualify for %s)%n",
                        metrics.userId(), currentTier, qualifiedTier);
            }

            return Optional.of(sub);
        } finally {
            lock.unlock();
        }
    }

    /**
     * SYSTEM-TRIGGERED batch entry point for the scheduled job (e.g. nightly, or at month start
     * when monthly metrics roll over). Each user is locked individually inside
     * {@link #reevaluateEarnedTier(UserMetrics)}, so the batch never holds one user's lock while
     * processing others, and user-facing calls are only blocked for their own user.
     */
    public void reevaluateEarnedTiers(Collection<UserMetrics> allUserMetrics) {
        allUserMetrics.forEach(this::reevaluateEarnedTier);
    }

    /** Looks up the user's current subscription, lazily marking it EXPIRED if its end date has passed. */
    public Optional<UserSubscription> getSubscription(String userId) {
        ReentrantLock lock = userLockManager.lockFor(userId);
        lock.lock();
        try {
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
        } finally {
            lock.unlock();
        }
    }

    public void cancelSubscription(String userId) {
        ReentrantLock lock = userLockManager.lockFor(userId);
        lock.lock();
        try {
            UserSubscription sub = subscriptionRepository.findActiveByUserId(userId)
                    .orElseThrow(() -> new IllegalStateException("No subscription found for user: " + userId));

            // Marks the subscription CANCELLED; endDate is left untouched
            sub.cancel();
            System.out.printf("[SUBSCRIPTION LOG] Cancelled subscription %s for user %s%n", sub.getSubscriptionId(), userId);
        } finally {
            lock.unlock();
        }
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
                .flatMap(sub -> tierRepository.findByLevel(sub.getEffectiveTier()))
                .map(MembershipTier::getBenefits)
                .orElse(List.of());
    }

    // ---------------------------------------------------------------
    // Lookup helpers
    // ---------------------------------------------------------------

    private MembershipPlan findPlan(String planId) {
        return planRepository.findById(planId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid Plan ID: " + planId));
    }

    private MembershipTier findTier(TierLevel tierLevel) {
        return tierRepository.findByLevel(tierLevel)
                .orElseThrow(() -> new IllegalArgumentException("Invalid Tier Level: " + tierLevel));
    }

    private UserSubscription findUsableSubscription(String userId) {
        return subscriptionRepository.findActiveByUserId(userId)
                .filter(s -> !s.isExpired())
                .orElseThrow(() -> new IllegalStateException("No active subscription found for user: " + userId));
    }
}
