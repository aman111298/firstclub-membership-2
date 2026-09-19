package com.aman.firstclubmembership2.domain;

import com.aman.firstclubmembership2.enums.SubscriptionStatus;
import com.aman.firstclubmembership2.enums.TierLevel;
import com.aman.firstclubmembership2.model.PaymentContext;

import java.time.LocalDateTime;

public class UserSubscription {

    private final String subscriptionId;
    private final String userId;
    private final String planId;
    private TierLevel tierLevel;
    private SubscriptionStatus status;
    private final LocalDateTime startDate;
    private final LocalDateTime endDate;
    private final PaymentContext paymentContext;

    public UserSubscription(String subscriptionId, String userId, String planId, TierLevel tierLevel,
                             int durationDays, PaymentContext paymentContext) {
        this.subscriptionId = subscriptionId;
        this.userId = userId;
        this.planId = planId;
        this.tierLevel = tierLevel;
        this.status = SubscriptionStatus.ACTIVE;
        this.startDate = LocalDateTime.now();
        this.endDate = this.startDate.plusDays(durationDays);
        this.paymentContext = paymentContext;
    }

    public synchronized void updateTier(TierLevel newTier) {
        this.tierLevel = newTier;
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

    public synchronized TierLevel getTierLevel() {
        return tierLevel;
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
