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
        int tokensToConsume = Math.max(0, estimatedTokens);
        TenantGatewayConfigurationProperties.TenantLimits limits = properties.limitsFor(tenantId);

        AtomicInteger requests = requestCache.get(tenantId, k -> new AtomicInteger(0));
        if (!tryConsume(requests, 1, limits.getMaxRequestsPerMinute())) {
            return RateLimitDecision.deny(
                    RateLimitDefaults.RATE_LIMIT_EXCEEDED_REASON,
                    RateLimitDefaults.REQUEST_RETRY_AFTER_SECONDS
            );
        }

        AtomicInteger tokens = tokenCache.get(tenantId, k -> new AtomicInteger(0));
        if (!tryConsume(tokens, tokensToConsume, limits.getMaxTokensPerDay())) {
            return RateLimitDecision.deny(
                    RateLimitDefaults.QUOTA_EXCEEDED_REASON,
                    RateLimitDefaults.TOKEN_RETRY_AFTER_SECONDS
            );
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

    private boolean tryConsume(AtomicInteger counter, int amount, int limit) {
        while (true) {
            int current = counter.get();
            if ((long) current + amount > limit) {
                return false;
            }
            if (counter.compareAndSet(current, current + amount)) {
                return true;
            }
        }
    }

}
