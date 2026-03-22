package dev.evancaplan.spring_ai_tenant_gateway.ratelimit;

import dev.evancaplan.spring_ai_tenant_gateway.config.TenantGatewayConfigurationProperties;
import dev.evancaplan.spring_ai_tenant_gateway.tenant.TenantContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CaffeineTenantRateLimiterTest {

    private TenantGatewayConfigurationProperties properties;
    private CaffeineTenantRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        properties = new TenantGatewayConfigurationProperties();
        properties.setDefaultMaxRequestsPerMinute(2);
        properties.setDefaultMaxTokensPerDay(100);
        rateLimiter = new CaffeineTenantRateLimiter(properties);
    }

    @Test
    void checkAndConsume_AllowsRequestsWithinLimits() {
        TenantContext context = new TenantContext("tenant-1", "team-1");
        
        RateLimitDecision rateLimitDecisionOne = rateLimiter.checkAndConsume(context, 10);
        assertThat(rateLimitDecisionOne.allowed()).isTrue();
        
        RateLimitDecision rateLimitDecisionTwo = rateLimiter.checkAndConsume(context, 10);
        assertThat(rateLimitDecisionTwo.allowed()).isTrue();
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
        
        // tenant one is now at its limit for requests (2)
        assertThat(rateLimiter.checkAndConsume(tenantContext, 1).allowed()).isFalse();
        
        // tenant two should still be allowed
        assertThat(rateLimiter.checkAndConsume(otherTenantContext, 10).allowed()).isTrue();
    }

    @Test
    void checkAndConsume_HonorsPerTenantOverrides() {
        TenantGatewayConfigurationProperties.TenantLimits override = new TenantGatewayConfigurationProperties.TenantLimits(5, 500);
        properties.getTenants().put("special-tenant", override);
        
        TenantContext context = new TenantContext("special-tenant", "team-special");
        
        // Should allow 5 requests (default was 2)
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
        assertThat(snapshot.maxRequestsPerMinute()).isEqualTo(2);
        assertThat(snapshot.tokensToday()).isEqualTo(40);
        assertThat(snapshot.maxTokensPerDay()).isEqualTo(100);
    }

    @Test
    void checkAndConsume_DeniedRequestsDoNotIncreaseUsage() {
        TenantContext context = new TenantContext("tenant-1", "team-1");

        assertThat(rateLimiter.checkAndConsume(context, 10).allowed()).isTrue();
        assertThat(rateLimiter.checkAndConsume(context, 10).allowed()).isTrue();
        assertThat(rateLimiter.checkAndConsume(context, 10).allowed()).isFalse();

        TenantUsageSnapshot snapshot = rateLimiter.getUsage("tenant-1");
        assertThat(snapshot.requestsThisMinute()).isEqualTo(2);
        assertThat(snapshot.tokensToday()).isEqualTo(20);
    }
}
