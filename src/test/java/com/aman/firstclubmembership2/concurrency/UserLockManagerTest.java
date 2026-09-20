package com.aman.firstclubmembership2.concurrency;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserLockManagerTest {

    @Test
    void lockFor_returnsTheSameLockInstanceForTheSameUser() {
        UserLockManager lockManager = new UserLockManager();

        ReentrantLock first = lockManager.lockFor("USER_1");
        ReentrantLock second = lockManager.lockFor("USER_1");

        assertSame(first, second, "the same user must always get the same lock instance");
    }

    @Test
    void lockFor_returnsDifferentLockInstancesForDifferentUsers() {
        UserLockManager lockManager = new UserLockManager();

        ReentrantLock userA = lockManager.lockFor("USER_A");
        ReentrantLock userB = lockManager.lockFor("USER_B");

        assertTrue(userA != userB, "different users must get independent locks");
    }

    /**
     * Proves the lock actually serializes concurrent access for the SAME user: without it,
     * "read counter, sleep, write counter+1" from many threads would lose updates and the
     * final value would be less than threadCount. With the lock, no update can be lost.
     */
    @Test
    void sameUsersLock_serializesConcurrentCriticalSections() throws InterruptedException {
        UserLockManager lockManager = new UserLockManager();
        int threadCount = 50;
        int[] counter = {0};

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                readyLatch.countDown();
                await(startLatch);

                ReentrantLock lock = lockManager.lockFor("SAME_USER");
                lock.lock();
                try {
                    int current = counter[0];
                    Thread.yield(); // widen the window a lost update would need
                    counter[0] = current + 1;
                } finally {
                    lock.unlock();
                }

                doneLatch.countDown();
            });
        }

        await(readyLatch);
        startLatch.countDown(); // release every thread at once
        assertTrue(doneLatch.await(10, TimeUnit.SECONDS), "all threads should finish");
        executor.shutdown();

        assertEquals(threadCount, counter[0], "no increment should be lost under the same-user lock");
    }

    /** Different users must not block each other - each gets an independent lock. */
    @Test
    void differentUsersLocks_doNotBlockEachOther() throws InterruptedException {
        UserLockManager lockManager = new UserLockManager();
        CountDownLatch userAHoldingLock = new CountDownLatch(1);
        CountDownLatch releaseUserA = new CountDownLatch(1);

        ExecutorService executor = Executors.newFixedThreadPool(1);
        executor.submit(() -> {
            ReentrantLock lockA = lockManager.lockFor("USER_A");
            lockA.lock();
            try {
                userAHoldingLock.countDown();
                await(releaseUserA);
            } finally {
                lockA.unlock();
            }
        });

        assertTrue(userAHoldingLock.await(5, TimeUnit.SECONDS));

        // USER_B's lock is independent, so this must succeed even while USER_A's lock is held
        ReentrantLock lockB = lockManager.lockFor("USER_B");
        assertTrue(lockB.tryLock(5, TimeUnit.SECONDS), "USER_B's lock should be free while only USER_A's lock is held");
        lockB.unlock();

        releaseUserA.countDown();
        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
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
