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
        	if (Thread.interrupted()) throw new InterruptedException();
            long now = System.currentTimeMillis();
            long last = lastSlotTime.get();
            long next = last + intervalMs;
            System.out.println(
            	    "Limiter="
            	    + System.identityHashCode(this)
            	);
            if (next <= now) {
                // slot is in the past, try to claim current time + interval
                if (lastSlotTime.compareAndSet(last, now)) {
                    return; // no sleep needed, fire immediately
                }
            } else {
                // slot is in the future
                if (lastSlotTime.compareAndSet(last, next)) {
                    Thread.sleep(next - now);
                    return;
                }
            }
            Thread.onSpinWait(); // hint to CPU, reduces spin overhead
        }
    }
}