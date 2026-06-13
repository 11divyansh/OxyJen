package io.oxyjen.resilience.ratelimit;

public interface RateLimiter {
    void acquire() throws InterruptedException;
    
    static RateLimiter.Builder builder() {
        return new Builder();
    }
    
    enum Algorithm {
        FIXED_INTERVAL
    }
    
    final class Builder {
        private int requestsPerMinute;
        private Algorithm algorithm = Algorithm.FIXED_INTERVAL;
        
        public Builder requestsPerMinute(int rpm) {
            this.requestsPerMinute = rpm;
            return this;
        }
        
        public Builder algorithm(Algorithm algorithm) {
            this.algorithm = algorithm;
            return this;
        }
        
        public RateLimiter build() {
        	if (requestsPerMinute <= 0) {
                throw new IllegalArgumentException(
                    "requestsPerMinute must be > 0, got: " + requestsPerMinute
                );
            }
            return switch (algorithm) {
                case FIXED_INTERVAL -> new FixedIntervalRateLimiter(requestsPerMinute);
            };
        }
    }
    
    public static RateLimiter geminiFreeTier() {
        return new FixedIntervalRateLimiter(4); // conservative buffer under 5
    }
}