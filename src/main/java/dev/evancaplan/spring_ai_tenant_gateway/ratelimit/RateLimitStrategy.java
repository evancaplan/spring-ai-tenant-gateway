package dev.evancaplan.spring_ai_tenant_gateway.ratelimit;

public enum RateLimitStrategy {
    IN_MEMORY,
    CAFFEINE,
    REDIS
}
