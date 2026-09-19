package com.aman.firstclubmembership2.repository;

import com.aman.firstclubmembership2.domain.UserSubscription;

import java.util.Optional;

public interface SubscriptionRepository {
    Optional<UserSubscription> findActiveByUserId(String userId);

    /** Registers the subscription as the user's current one and records it in history. */
    void save(UserSubscription subscription);
}
