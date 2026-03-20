package dev.evancaplan.spring_ai_tenant_gateway.ratelimit;

import dev.evancaplan.spring_ai_tenant_gateway.tenant.TenantContext;

public interface TenantRateLimiter {
    RateLimitDecision checkAndConsume(TenantContext tenantContext, int estimatedTokens);
    TenantUsageSnapshot getUsage(String tenantId);
}
