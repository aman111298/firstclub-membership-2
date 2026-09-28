package com.aman.firstclubmembership2.domain;

import com.aman.firstclubmembership2.enums.SubscriptionStatus;
import com.aman.firstclubmembership2.enums.TierLevel;
import com.aman.firstclubmembership2.model.PaymentContext;

import java.time.LocalDateTime;

/**
 * A user's membership: a plan (duration + price) and a tier (benefits).
 *
 * The tier has two independent sources:
 * - purchasedTier: what the user paid for; changes only through a paid upgrade or an admin downgrade.
 * - earnedTier: the highest tier the user's activity has qualified them for; only ever raised by the
 *   scheduled/event-driven re-evaluation (never at purchase time, null until the first upgrade),
 *   and cleared only by an admin downgrade.
 * Benefits follow the effective tier = the higher of the two.
 */
public class UserSubscription {

    private final String subscriptionId;
    private final String userId;
    private final String planId;
    private TierLevel purchasedTier;
    private TierLevel earnedTier;
    private SubscriptionStatus status;
    private final LocalDateTime startDate;
    private final LocalDateTime endDate;
    private final PaymentContext paymentContext;

    public UserSubscription(String subscriptionId, String userId, String planId, TierLevel purchasedTier,
                             int durationDays, PaymentContext paymentContext) {
        this.subscriptionId = subscriptionId;
        this.userId = userId;
        this.planId = planId;
        this.purchasedTier = purchasedTier;
        this.earnedTier = null; // not evaluated at purchase - the user gets exactly what they bought
        this.status = SubscriptionStatus.ACTIVE;
        this.startDate = LocalDateTime.now();
        this.endDate = this.startDate.plusDays(durationDays);
        this.paymentContext = paymentContext;
    }

    /** Paid upgrade: raises what the user owns. */
    public synchronized void upgradePurchasedTier(TierLevel newTier) {
        this.purchasedTier = newTier;
    }

    /**
     * Admin penalty: lowers what the user owns and clears the earned tier, so the effective tier
     * drops right away. The next scheduled re-evaluation may earn a higher tier back.
     */
    public synchronized void downgradeByAdmin(TierLevel newTier) {
        this.purchasedTier = newTier;
        this.earnedTier = null;
    }

    /** Records a tier earned through activity (the service only calls this to move a user up). */
    public synchronized void setEarnedTier(TierLevel earnedTier) {
        this.earnedTier = earnedTier;
    }

    /** The tier whose benefits apply: the higher of purchased and earned. */
    public synchronized TierLevel getEffectiveTier() {
        if (earnedTier == null || earnedTier.getRank() <= purchasedTier.getRank()) {
            return purchasedTier;
        }
        return earnedTier;
    }

    public synchronized void cancel() {
        this.status = SubscriptionStatus.CANCELLED;
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(endDate) || status != SubscriptionStatus.ACTIVE;
    }

    public String getSubscriptionId() {
        return subscriptionId;
    }

    public String getUserId() {
        return userId;
    }

    public String getPlanId() {
        return planId;
    }

    public synchronized TierLevel getPurchasedTier() {
        return purchasedTier;
    }

    /** Null until activity first upgrades the user (or after an admin downgrade). */
    public synchronized TierLevel getEarnedTier() {
        return earnedTier;
    }

    public synchronized SubscriptionStatus getStatus() {
        return status;
    }

    public synchronized void setStatus(SubscriptionStatus status) {
        this.status = status;
    }

    public LocalDateTime getStartDate() {
        return startDate;
    }

    public LocalDateTime getEndDate() {
        return endDate;
    }

    public PaymentContext getPaymentContext() {
        return paymentContext;
    }
}
