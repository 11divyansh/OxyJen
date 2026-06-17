package io.oxyjen.resilience.ratelimit;

import java.util.concurrent.atomic.AtomicLong;

public final class AdaptiveRateLimiter implements RateLimiter {

    private final long baseIntervalMs;
    private final AtomicLong blockedUntil = new AtomicLong(0);
    // CAS slot scheduling, same as FixedIntervalRateLimiter
    private final AtomicLong lastSlotTime = new AtomicLong(0);

    public AdaptiveRateLimiter(int requestsPerMinute) {
        this.baseIntervalMs = 60_000L / requestsPerMinute;
    }

    @Override
    public void acquire() throws InterruptedException {
    	while (true) {
            if (Thread.interrupted()) throw new InterruptedException();

            // wait until blockedUntil expires
            long now = System.currentTimeMillis();
            long blocked = blockedUntil.get();
            if (blocked > now) {
                long wait = blocked - now;
                System.out.println("[AdaptiveRateLimiter] "
                    + Thread.currentThread().getName()
                    + " blocked until quota resets, waiting " + wait + "ms");
                Thread.sleep(wait);
                // after sleeping, loop back and re-check
                // blockedUntil might have been updated while we slept
                continue;
            }

            // try to reserve CAS slot
            now = System.currentTimeMillis();
            long last = lastSlotTime.get();
            long next = last + baseIntervalMs;

            if (next <= now) {
                if (lastSlotTime.compareAndSet(last, now)) {
                    // re-check blockedUntil after CAS success
                    // another thread might have received 429 between
                    // our blockedUntil check and CAS
                    if (blockedUntil.get() > System.currentTimeMillis()) {
                        // blockedUntil changed while we were doing CAS
                        // give back the slot and retry from top
                        lastSlotTime.compareAndSet(now, last); // best effort rollback
                        continue;
                    }
                    return; // fire immediately
                }
            } else {
                if (lastSlotTime.compareAndSet(last, next)) {
                    // re-check blockedUntil after CAS
                    long recheck = blockedUntil.get();
                    if (recheck > next) {
                        // blockedUntil is beyond our slot, retry
                        lastSlotTime.compareAndSet(next, last); // best effort rollback
                        continue;
                    }
                    long sleepMs = next - System.currentTimeMillis();
                    if (sleepMs > 0) Thread.sleep(sleepMs);
                    // recheck after sleep
                    // blockedUntil may have been updated while we slept
                    if (blockedUntil.get() > System.currentTimeMillis()) {
                        continue; // back to top
                    }
                    return;
                }
            }
            Thread.onSpinWait();
        }
    }

    /**
     * Called by LLMChain when provider returns 429 with Retry-After.
     * Updates shared blocked-until so ALL waiting threads respect it.
     */
    public void on429(long retryAfterMs) {
        long unblockAt = System.currentTimeMillis() + retryAfterMs;
        // only move forward, never back
        blockedUntil.updateAndGet(current -> Math.max(current, unblockAt));
        // also push lastSlotTime forward so CAS scheduling respects the block
        lastSlotTime.updateAndGet(current -> Math.max(current, unblockAt));
        System.out.println("[AdaptiveRateLimiter] Provider blocked until +" 
            + retryAfterMs + "ms. All threads will wait.");
    }

    public long getBlockedUntil() { return blockedUntil.get(); }
}