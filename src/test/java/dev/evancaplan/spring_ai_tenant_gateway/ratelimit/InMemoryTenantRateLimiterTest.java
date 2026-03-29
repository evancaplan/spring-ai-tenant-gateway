package dev.evancaplan.spring_ai_tenant_gateway.ratelimit;

import dev.evancaplan.spring_ai_tenant_gateway.config.TenantGatewayConfigurationProperties;
import dev.evancaplan.spring_ai_tenant_gateway.tenant.TenantContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryTenantRateLimiterTest {

    private TenantGatewayConfigurationProperties properties;
    private InMemoryTenantRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        properties = new TenantGatewayConfigurationProperties();
        properties.setDefaultMaxRequestsPerMinute(2);
        properties.setDefaultMaxTokensPerDay(100);
        rateLimiter = new InMemoryTenantRateLimiter(properties);
    }

    @Test
    void checkAndConsume_AllowsRequestsWithinLimits() {
        TenantContext context = new TenantContext("tenant-1", "team-1");
        
        assertThat(rateLimiter.checkAndConsume(context, 10).allowed()).isTrue();
        assertThat(rateLimiter.checkAndConsume(context, 10).allowed()).isTrue();
    }

    @Test
    void checkAndConsume_DeniesWhenRateLimitExceeded() {
        TenantContext context = new TenantContext("tenant-1", "team-1");
        
        rateLimiter.checkAndConsume(context, 10);
        rateLimiter.checkAndConsume(context, 10);
        
        RateLimitDecision rateLimitDecision = rateLimiter.checkAndConsume(context, 10);
        assertThat(rateLimitDecision.allowed()).isFalse();
        assertThat(rateLimitDecision.reason()).contains("rate_limit_exceeded");
    }

    @Test
    void checkAndConsume_DeniesWhenTokenQuotaExceeded() {
        TenantContext context = new TenantContext("tenant-1", "team-1");
        
        RateLimitDecision rateLimitDecision = rateLimiter.checkAndConsume(context, 101);
        assertThat(rateLimitDecision.allowed()).isFalse();
        assertThat(rateLimitDecision.reason()).contains("quota_exceeded");
    }

    @Test
    void checkAndConsume_IsolatedBetweenTenants() {
        TenantContext tenantContext = new TenantContext("tenant-1", "team-1");
        TenantContext otherTenantContext = new TenantContext("tenant-2", "team-2");
        
        rateLimiter.checkAndConsume(tenantContext, 50);
        rateLimiter.checkAndConsume(tenantContext, 50);
        
        assertThat(rateLimiter.checkAndConsume(tenantContext, 1).allowed()).isFalse();
        assertThat(rateLimiter.checkAndConsume(otherTenantContext, 10).allowed()).isTrue();
    }

    @Test
    void checkAndConsume_HonorsPerTenantOverrides() {
        TenantGatewayConfigurationProperties.TenantLimits override = new TenantGatewayConfigurationProperties.TenantLimits(5, 500);
        properties.getTenants().put("special-tenant", override);
        
        TenantContext context = new TenantContext("special-tenant", "team-special");
        
        for (int i = 0; i < 5; i++) {
            assertThat(rateLimiter.checkAndConsume(context, 10).allowed()).isTrue();
        }
        assertThat(rateLimiter.checkAndConsume(context, 10).allowed()).isFalse();
    }

    @Test
    void getUsage_ReturnsCorrectSnapshot() {
        TenantContext context = new TenantContext("tenant-1", "team-1");
        rateLimiter.checkAndConsume(context, 40);
        
        TenantUsageSnapshot snapshot = rateLimiter.getUsage("tenant-1");
        assertThat(snapshot.tenantId()).isEqualTo("tenant-1");
        assertThat(snapshot.requestsThisMinute()).isEqualTo(1);
        assertThat(snapshot.tokensToday()).isEqualTo(40);
    }

    @Test
    void checkAndConsume_ConcurrentRequests_DoesNotExceedConfiguredLimit() throws InterruptedException {
        TenantGatewayConfigurationProperties concurrencyProps = new TenantGatewayConfigurationProperties();
        concurrencyProps.setDefaultMaxRequestsPerMinute(5);
        concurrencyProps.setDefaultMaxTokensPerDay(1_000);
        InMemoryTenantRateLimiter concurrentLimiter = new InMemoryTenantRateLimiter(concurrencyProps);
        TenantContext context = new TenantContext("tenant-concurrent", "team-1");

        int requests = 20;
        AtomicInteger allowed = new AtomicInteger(0);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(requests);
        ExecutorService pool = Executors.newFixedThreadPool(requests);

        for (int i = 0; i < requests; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    if (concurrentLimiter.checkAndConsume(context, 1).allowed()) {
                        allowed.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
        pool.shutdownNow();

        assertThat(allowed.get()).isEqualTo(5);
        TenantUsageSnapshot snapshot = concurrentLimiter.getUsage("tenant-concurrent");
        assertThat(snapshot.requestsThisMinute()).isEqualTo(5);
    }
}
