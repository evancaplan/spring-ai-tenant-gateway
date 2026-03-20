package dev.evancaplan.spring_ai_tenant_gateway.ratelimit;

public record RateLimitDecision(boolean allowed, String reason, long retryAfterSeconds) {

    public static RateLimitDecision permit() {
        return new RateLimitDecision(true, null, 0);
    }

    public static RateLimitDecision deny(String reason, long retryAfterSeconds) {
        return new RateLimitDecision(false, reason, retryAfterSeconds);
    }
}
