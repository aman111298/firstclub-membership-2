package com.aman.firstclubmembership2.store;

import java.util.concurrent.atomic.AtomicLong;

public class IdGenerator {

    private final AtomicLong planSeq = new AtomicLong(0);
    private final AtomicLong tierSeq = new AtomicLong(0);
    private final AtomicLong subscriptionSeq = new AtomicLong(0);
    private final AtomicLong paymentLogSeq = new AtomicLong(0);

    public String nextPlanId() {
        return "plan_" + planSeq.incrementAndGet();
    }

    public String nextTierId() {
        return "tier_" + tierSeq.incrementAndGet();
    }

    public String nextSubscriptionId() {
        return "sub_" + subscriptionSeq.incrementAndGet();
    }

    public String nextPaymentLogId() {
        return "pay_" + paymentLogSeq.incrementAndGet();
    }
}
