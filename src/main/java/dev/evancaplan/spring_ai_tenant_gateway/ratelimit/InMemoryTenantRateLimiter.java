package dev.evancaplan.spring_ai_tenant_gateway.ratelimit;

import dev.evancaplan.spring_ai_tenant_gateway.config.TenantGatewayConfigurationProperties;
import dev.evancaplan.spring_ai_tenant_gateway.tenant.TenantContext;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class InMemoryTenantRateLimiter implements TenantRateLimiter {

    private final TenantGatewayConfigurationProperties properties;
    private final ConcurrentHashMap<String, WindowCounter> requestCounters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, WindowCounter> tokenCounters = new ConcurrentHashMap<>();

    public InMemoryTenantRateLimiter(TenantGatewayConfigurationProperties properties) {
        this.properties = properties;
    }

    @Override
    public RateLimitDecision checkAndConsume(TenantContext context, int estimatedTokens) {
        String tenantId = context.tenantId();
        int tokensToConsume = Math.max(0, estimatedTokens);
        TenantGatewayConfigurationProperties.TenantLimits limits = properties.limitsFor(tenantId);

        WindowCounter requests = requestCounters.computeIfAbsent(
                tenantId,
                k -> new WindowCounter(RateLimitDefaults.REQUEST_WINDOW_SECONDS, limits.getMaxRequestsPerMinute())
        );
        if (!requests.tryConsume(1)) {
            return RateLimitDecision.deny(
                    RateLimitDefaults.RATE_LIMIT_EXCEEDED_REASON,
                    RateLimitDefaults.REQUEST_RETRY_AFTER_SECONDS
            );
        }

        WindowCounter tokens = tokenCounters.computeIfAbsent(
                tenantId,
                k -> new WindowCounter(RateLimitDefaults.TOKEN_WINDOW_SECONDS, limits.getMaxTokensPerDay())
        );
        if (!tokens.tryConsume(tokensToConsume)) {
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
        WindowCounter requests = requestCounters.get(tenantId);
        WindowCounter tokens = tokenCounters.get(tenantId);

        return new TenantUsageSnapshot(
                tenantId,
                requests != null ? requests.getCount() : 0,
                limits.getMaxRequestsPerMinute(),
                tokens != null ? tokens.getCount() : 0,
                limits.getMaxTokensPerDay()
        );
    }

    static class WindowCounter {
        private final long windowSeconds;
        private final int limit;
        private final AtomicInteger count = new AtomicInteger(0);
        private volatile Instant windowStart = Instant.now();

        WindowCounter(long windowSeconds, int limit) {
            this.windowSeconds = windowSeconds;
            this.limit = limit;
        }

        synchronized boolean tryConsume(int amount) {
            maybeReset();
            int current = count.get();
            if ((long) current + amount > limit) {
                return false;
            }
            count.addAndGet(amount);
            return true;
        }

        private void maybeReset() {
            Instant now = Instant.now();
            if (!now.isBefore(windowStart.plusSeconds(windowSeconds))) {
                count.set(0);
                windowStart = now;
            }
        }

        int getCount() {
            maybeReset();
            return count.get();
        }
    }
}
