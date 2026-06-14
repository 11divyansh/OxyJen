package io.oxyjen.resilience.ratelimit;

import java.util.concurrent.atomic.AtomicLong;

final class FixedIntervalRateLimiter implements RateLimiter {

    private final long intervalMs;
    private final AtomicLong lastSlotTime = new AtomicLong(0);

    FixedIntervalRateLimiter(int requestsPerMinute) {
        this.intervalMs = 60_000L / requestsPerMinute;
    }

    @Override
    public void acquire() throws InterruptedException {
        while (true) {
            long now = System.currentTimeMillis();
            long last = lastSlotTime.get();
            long next = Math.max(now, last + intervalMs);
            
            if (lastSlotTime.compareAndSet(last, next)) {
                long sleepMs = next - now;
                if (sleepMs > 0) {
                    System.out.println("[RateLimiter] throttling for " + sleepMs + "ms");
                    Thread.sleep(sleepMs);
                } else {
                    System.out.println("[RateLimiter] slot acquired immediately");
                }
                return;
            }
            Thread.onSpinWait(); // hint to CPU, reduces spin overhead
        }
    }
}