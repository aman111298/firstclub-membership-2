package com.aman.firstclubmembership2.store;

import com.aman.firstclubmembership2.domain.MembershipPlan;
import com.aman.firstclubmembership2.domain.MembershipTier;
import com.aman.firstclubmembership2.domain.UserSubscription;
import com.aman.firstclubmembership2.model.PaymentContext;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DataStore {

    public final IdGenerator idGenerator = new IdGenerator();

    public final Map<String, MembershipPlan> plans = new ConcurrentHashMap<>();
    public final Map<String, MembershipTier> tiers = new ConcurrentHashMap<>();
    public final Map<String, UserSubscription> activeSubscriptions = new ConcurrentHashMap<>();
    public final Map<String, UserSubscription> subscriptionHistory = new ConcurrentHashMap<>();
    public final Map<String, PaymentContext> paymentLogs = new ConcurrentHashMap<>();
}
