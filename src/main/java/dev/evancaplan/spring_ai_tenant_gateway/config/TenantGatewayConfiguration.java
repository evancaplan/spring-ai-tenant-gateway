package dev.evancaplan.spring_ai_tenant_gateway.config;

import dev.evancaplan.spring_ai_tenant_gateway.ratelimit.CaffeineTenantRateLimiter;
import dev.evancaplan.spring_ai_tenant_gateway.ratelimit.InMemoryTenantRateLimiter;
import dev.evancaplan.spring_ai_tenant_gateway.ratelimit.RedisTenantRateLimiter;
import dev.evancaplan.spring_ai_tenant_gateway.ratelimit.TenantRateLimiter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class TenantGatewayConfiguration {

    @Bean
    @ConditionalOnProperty(
            name = "ai.tenant.gateway.rate-limit-strategy",
            havingValue = "IN_MEMORY"
    )
    public TenantRateLimiter inMemoryTenantRateLimiter(TenantGatewayConfigurationProperties properties) {
        return new InMemoryTenantRateLimiter(properties);
    }

    @Bean
    @ConditionalOnProperty(
            name = "ai.tenant.gateway.rate-limit-strategy",
            havingValue = "CAFFEINE",
            matchIfMissing = true
    )
    public TenantRateLimiter caffeineTenantRateLimiter(TenantGatewayConfigurationProperties properties) {
        return new CaffeineTenantRateLimiter(properties);
    }

    @Bean
    @ConditionalOnProperty(
            name = "ai.tenant.gateway.rate-limit-strategy",
            havingValue = "REDIS"
    )
    public TenantRateLimiter redisTenantRateLimiter(StringRedisTemplate redis, TenantGatewayConfigurationProperties properties) {
        return new RedisTenantRateLimiter(redis, properties);
    }
}

