package com.aman.firstclubmembership2.concurrency;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Hands out one {@link ReentrantLock} per user, lazily created, so callers can serialize
 * every state-changing operation for one user (e.g. two subscribe() calls racing) while
 * different users never block each other. Callers are responsible for lock() / unlock()
 * in a try/finally around their critical section.
 */
public class UserLockManager {

    private final Map<String, ReentrantLock> locksByUserId = new ConcurrentHashMap<>();

    public ReentrantLock lockFor(String userId) {
        return locksByUserId.computeIfAbsent(userId, id -> new ReentrantLock());
    }
}
