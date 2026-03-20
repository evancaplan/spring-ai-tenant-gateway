package dev.evancaplan.spring_ai_tenant_gateway.ratelimit;

import dev.evancaplan.spring_ai_tenant_gateway.config.TenantGatewayConfigurationProperties;
import dev.evancaplan.spring_ai_tenant_gateway.tenant.TenantContext;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
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
        TenantGatewayConfigurationProperties.TenantLimits limits = properties.limitsFor(tenantId);

        WindowCounter requests = requestCounters.computeIfAbsent(tenantId, k -> new WindowCounter(60, limits.getMaxRequestsPerMinute()));
        if (!requests.tryConsume(1)) {
            return RateLimitDecision.deny("rate_limit_exceeded: too many requests per minute", 30);
        }

        WindowCounter tokens = tokenCounters.computeIfAbsent(tenantId, k -> new WindowCounter(86_400, limits.getMaxTokensPerDay()));
        if (!tokens.tryConsume(estimatedTokens)) {
            return RateLimitDecision.deny("quota_exceeded: daily token limit reached", 3600);
        }

        return RateLimitDecision.permit();
    }

    @Override
    public TenantUsageSnapshot getUsage(String tenantId) {
        TenantGatewayConfigurationProperties.TenantLimits limits = properties.limitsFor(tenantId);
        WindowCounter requests = requestCounters.getOrDefault(tenantId, new WindowCounter(60, limits.getMaxRequestsPerMinute()));
        WindowCounter tokens = tokenCounters.getOrDefault(tenantId, new WindowCounter(86_400, limits.getMaxTokensPerDay()));

        return new TenantUsageSnapshot(
                tenantId,
                requests.count.get(),
                limits.getMaxRequestsPerMinute(),
                tokens.count.get(),
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

        boolean tryConsume(int amount) {
            maybeReset();
            int current = count.get();
            if (current + amount > limit) return false;
            count.addAndGet(amount);
            return true;
        }

        private void maybeReset() {
            Instant now = Instant.now();
            if (now.isAfter(windowStart.plusSeconds(windowSeconds))) {
                count.set(0);
                windowStart = now;
            }
        }
    }
}
