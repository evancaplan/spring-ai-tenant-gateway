package dev.evancaplan.spring_ai_tenant_gateway.config;

import dev.evancaplan.spring_ai_tenant_gateway.ratelimit.CaffeineTenantRateLimiter;
import dev.evancaplan.spring_ai_tenant_gateway.ratelimit.InMemoryTenantRateLimiter;
import dev.evancaplan.spring_ai_tenant_gateway.ratelimit.TenantRateLimiter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TenantGatewayConfiguration {

    private final TenantGatewayConfigurationProperties properties;

    public TenantGatewayConfiguration(TenantGatewayConfigurationProperties properties) {
        this.properties = properties;
    }

    @Bean
    @ConditionalOnProperty(
            name = "ai.tenant.gateway.rate-limit-strategy",
            havingValue = "IN_MEMORY"
    )
    public TenantRateLimiter inMemoryTenantRateLimiter() {
        return new InMemoryTenantRateLimiter(properties);
    }

    @Bean
    @ConditionalOnProperty(
            name = "ai.tenant.gateway.rate-limit-strategy",
            havingValue = "CAFFEINE",
            matchIfMissing = true
    )
    public TenantRateLimiter caffeineTenantRateLimiter() {
        return new CaffeineTenantRateLimiter(properties);
    }
}

