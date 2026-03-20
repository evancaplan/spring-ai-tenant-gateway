package dev.evancaplan.spring_ai_tenant_gateway.ratelimit;

public record TenantUsageSnapshot(
        String tenantId,
        int requestsThisMinute,
        int maxRequestsPerMinute,
        int tokensToday,
        int maxTokensPerDay
) {}
