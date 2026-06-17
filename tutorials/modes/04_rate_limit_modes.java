package tutorials.modes;

import io.oxyjen.resilience.ratelimit.RateLimiter;
import io.oxyjen.resilience.ratelimit.RateLimiters;

/**
 * Modes tutorial 4:
 * Fixed and adaptive rate limiting.
 */
final class RateLimitModesTutorial {

    private RateLimitModesTutorial() {}

    public static void main(String[] args) {
        RateLimiter fixed = RateLimiters.fixedInterval(4);
        RateLimiter adaptive = RateLimiters.adaptive(4);
        RateLimiter geminiFree = RateLimiters.geminiFreeTier();

        System.out.println(fixed.getClass().getSimpleName());
        System.out.println(adaptive.getClass().getSimpleName());
        System.out.println(geminiFree.getClass().getSimpleName());
    }
}
