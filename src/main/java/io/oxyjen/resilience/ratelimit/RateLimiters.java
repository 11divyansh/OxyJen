package io.oxyjen.resilience.ratelimit;

public final class RateLimiters {
    
    private RateLimiters() {}
    
    public static RateLimiter fixedInterval(int requestsPerMinute) {
        return new FixedIntervalRateLimiter(requestsPerMinute);
    }
    
    // provider profiles - safe defaults out of the box
    public static RateLimiter geminiFreeTier() {
        // conservative fixed interval, quota=5 per observation
        return new FixedIntervalRateLimiter(4); // conservative buffer under 5
    }
}