package dev.evancaplan.spring_ai_tenant_gateway.ratelimit;

import dev.evancaplan.spring_ai_tenant_gateway.config.TenantGatewayConfigurationProperties;
import dev.evancaplan.spring_ai_tenant_gateway.tenant.TenantContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RedisTenantRateLimiterTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private RedisTenantRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        TenantGatewayConfigurationProperties properties = new TenantGatewayConfigurationProperties();
        properties.setDefaultMaxRequestsPerMinute(2);
        properties.setDefaultMaxTokensPerDay(100);
        
        rateLimiter = new RedisTenantRateLimiter(redisTemplate, properties);
    }

    @Test
    void checkAndConsume_AllowsRequestsWithinLimits() {
        TenantContext context = new TenantContext("tenant-1", "team-1");

        when(redisTemplate.execute(
                any(RedisScript.class),
                anyList(),
                any(),
                any(),
                any(),
                any(),
                any())
        ).thenReturn(0L);

        RateLimitDecision rateLimitDecision = rateLimiter.checkAndConsume(context, 10);
        assertThat(rateLimitDecision.allowed()).isTrue();
    }

    @Test
    void checkAndConsume_DeniesWhenRateLimitExceeded() {
        TenantContext context = new TenantContext("tenant-1", "team-1");

        when(redisTemplate.execute(
                any(RedisScript.class),
                anyList(),
                any(),
                any(),
                any(),
                any(),
                any())
        ).thenReturn(1L);

        RateLimitDecision rateLimitDecision = rateLimiter.checkAndConsume(context, 10);
        assertThat(rateLimitDecision.allowed()).isFalse();
        assertThat(rateLimitDecision.reason()).contains("rate_limit_exceeded");
    }

    @Test
    void checkAndConsume_DeniesWhenTokenQuotaExceeded() {
        TenantContext context = new TenantContext("tenant-1", "team-1");

        when(redisTemplate.execute(
                any(RedisScript.class),
                anyList(),
                any(),
                any(),
                any(),
                any(),
                any())
        ).thenReturn(2L);

        RateLimitDecision rateLimitDecision = rateLimiter.checkAndConsume(context, 10);
        assertThat(rateLimitDecision.allowed()).isFalse();
        assertThat(rateLimitDecision.reason()).contains("quota_exceeded");
    }

    @Test
    void checkAndConsume_UsesRequestAndTokenKeys() {
        TenantContext context = new TenantContext("tenant-123", "team-1");

        when(redisTemplate.execute(
                any(RedisScript.class),
                anyList(),
                any(),
                any(),
                any(),
                any(),
                any())
        ).thenReturn(0L);

        rateLimiter.checkAndConsume(context, 5);

        verify(redisTemplate).execute(
                any(RedisScript.class),
                eq(List.of("rate:tenant-123:requests", "rate:tenant-123:tokens")),
                eq("2"),
                eq("100"),
                eq("5"),
                eq("60"),
                eq("86400")
        );
    }

    @Test
    void getUsage_ReturnsCorrectSnapshot() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(contains("requests"))).thenReturn("1");
        when(valueOperations.get(contains("tokens"))).thenReturn("40");
        
        TenantUsageSnapshot snapshot = rateLimiter.getUsage("tenant-1");
        assertThat(snapshot.tenantId()).isEqualTo("tenant-1");
        assertThat(snapshot.requestsThisMinute()).isEqualTo(1);
        assertThat(snapshot.tokensToday()).isEqualTo(40);
    }
}
