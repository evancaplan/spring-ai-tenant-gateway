package dev.evancaplan.spring_ai_tenant_gateway.ratelimit;

final class RateLimitDefaults {
    static final long REQUEST_WINDOW_SECONDS = 60;
    static final long TOKEN_WINDOW_SECONDS = 86_400;
    static final long REQUEST_RETRY_AFTER_SECONDS = 30;
    static final long TOKEN_RETRY_AFTER_SECONDS = 3_600;

    static final String RATE_LIMIT_EXCEEDED_REASON = "rate_limit_exceeded: too many requests per minute";
    static final String QUOTA_EXCEEDED_REASON = "quota_exceeded: daily token limit reached";

    private RateLimitDefaults() {
    }
}
