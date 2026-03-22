package dev.evancaplan.spring_ai_tenant_gateway.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.evancaplan.spring_ai_tenant_gateway.config.TenantGatewayConfigurationProperties;
import dev.evancaplan.spring_ai_tenant_gateway.tenant.TenantContext;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class CaffeineTenantRateLimiter implements TenantRateLimiter {
    private final TenantGatewayConfigurationProperties properties;

    // expires each entry 1 minute after last write (auto-resets request window)
    private final Cache<String, AtomicInteger> requestCache = Caffeine.newBuilder()
            .expireAfterWrite(1, TimeUnit.MINUTES)
            .build();

    // expires each entry 1 day after last write (auto-resets token quota)
    private final Cache<String, AtomicInteger> tokenCache = Caffeine.newBuilder()
            .expireAfterWrite(1, TimeUnit.DAYS)
            .build();

    public CaffeineTenantRateLimiter(TenantGatewayConfigurationProperties properties) {
        this.properties = properties;
    }

    @Override
    public RateLimitDecision checkAndConsume(TenantContext context, int estimatedTokens) {
        String tenantId = context.tenantId();
        TenantGatewayConfigurationProperties.TenantLimits limits = properties.limitsFor(tenantId);

        AtomicInteger requests = requestCache.get(tenantId, k -> new AtomicInteger(0));
        if (requests.incrementAndGet() > limits.getMaxRequestsPerMinute()) {
            return RateLimitDecision.deny("rate_limit_exceeded: too many requests per minute", 30);
        }

        AtomicInteger tokens = tokenCache.get(tenantId, k -> new AtomicInteger(0));
        if (tokens.addAndGet(estimatedTokens) > limits.getMaxTokensPerDay()) {
            return RateLimitDecision.deny("quota_exceeded: daily token limit reached", 3600);
        }

        return RateLimitDecision.permit();
    }

    @Override
    public TenantUsageSnapshot getUsage(String tenantId) {
        TenantGatewayConfigurationProperties.TenantLimits limits = properties.limitsFor(tenantId);

        AtomicInteger requests = requestCache.getIfPresent(tenantId);
        AtomicInteger tokens = tokenCache.getIfPresent(tenantId);

        return new TenantUsageSnapshot(
                tenantId,
                requests != null ? requests.get() : 0,
                limits.getMaxRequestsPerMinute(),
                tokens != null ? tokens.get() : 0,
                limits.getMaxTokensPerDay()
        );
    }

}
