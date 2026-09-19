package com.aman.firstclubmembership2.repository;

import com.aman.firstclubmembership2.domain.UserSubscription;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemorySubscriptionRepository implements SubscriptionRepository {

    private final Map<String, UserSubscription> activeByUserId = new ConcurrentHashMap<>();
    private final Map<String, UserSubscription> historyBySubscriptionId = new ConcurrentHashMap<>();

    @Override
    public Optional<UserSubscription> findActiveByUserId(String userId) {
        return Optional.ofNullable(activeByUserId.get(userId));
    }

    @Override
    public void save(UserSubscription subscription) {
        activeByUserId.put(subscription.getUserId(), subscription);
        historyBySubscriptionId.put(subscription.getSubscriptionId(), subscription);
    }
}
