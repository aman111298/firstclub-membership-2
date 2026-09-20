package com.aman.firstclubmembership2.service;

import com.aman.firstclubmembership2.concurrency.UserLockManager;
import com.aman.firstclubmembership2.config.CatalogSeeder;
import com.aman.firstclubmembership2.domain.UserSubscription;
import com.aman.firstclubmembership2.enums.TierLevel;
import com.aman.firstclubmembership2.repository.InMemoryPaymentLogRepository;
import com.aman.firstclubmembership2.repository.InMemoryPlanRepository;
import com.aman.firstclubmembership2.repository.InMemorySubscriptionRepository;
import com.aman.firstclubmembership2.repository.InMemoryTierRepository;
import com.aman.firstclubmembership2.repository.PaymentLogRepository;
import com.aman.firstclubmembership2.repository.PlanRepository;
import com.aman.firstclubmembership2.repository.SubscriptionRepository;
import com.aman.firstclubmembership2.repository.TierRepository;
import com.aman.firstclubmembership2.store.IdGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Proves the UserLockManager closes the double-subscribe / double-charge race described in the README. */
class MembershipServiceConcurrencyTest {

    private MembershipService service;

    @BeforeEach
    void setUp() {
        IdGenerator idGenerator = new IdGenerator();
        PlanRepository planRepository = new InMemoryPlanRepository();
        TierRepository tierRepository = new InMemoryTierRepository();
        SubscriptionRepository subscriptionRepository = new InMemorySubscriptionRepository();
        PaymentLogRepository paymentLogRepository = new InMemoryPaymentLogRepository();
        UserLockManager userLockManager = new UserLockManager();

        CatalogSeeder.seed(planRepository, tierRepository, idGenerator);

        service = new MembershipService(planRepository, tierRepository, subscriptionRepository, paymentLogRepository, idGenerator, userLockManager);
    }

    @Test
    void concurrentSubscribeCallsForTheSameUser_exactlyOneSucceeds() throws InterruptedException {
        int threadCount = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger conflictCount = new AtomicInteger();
        List<Throwable> unexpectedFailures = new java.util.concurrent.CopyOnWriteArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                readyLatch.countDown();
                await(startLatch);
                try {
                    service.subscribe("USER_RACE", CatalogSeeder.MONTHLY_PLAN_ID, TierLevel.SILVER, "CREDIT_CARD");
                    successCount.incrementAndGet();
                } catch (IllegalStateException expectedConflict) {
                    conflictCount.incrementAndGet();
                } catch (Throwable unexpected) {
                    unexpectedFailures.add(unexpected);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        await(readyLatch);
        startLatch.countDown(); // release every thread at once to maximize the race window
        assertTrue(doneLatch.await(10, TimeUnit.SECONDS), "all threads should finish");
        executor.shutdown();

        assertTrue(unexpectedFailures.isEmpty(), "no unexpected exceptions: " + unexpectedFailures);
        assertEquals(1, successCount.get(), "exactly one subscribe() call should succeed");
        assertEquals(threadCount - 1, conflictCount.get(), "every other call should see an active subscription and be rejected");

        UserSubscription current = service.getSubscription("USER_RACE").orElseThrow();
        assertEquals(TierLevel.SILVER, current.getTierLevel());
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }
}
