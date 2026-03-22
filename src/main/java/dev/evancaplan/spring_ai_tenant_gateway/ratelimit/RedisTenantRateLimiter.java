package dev.evancaplan.spring_ai_tenant_gateway.ratelimit;

import dev.evancaplan.spring_ai_tenant_gateway.config.TenantGatewayConfigurationProperties;
import dev.evancaplan.spring_ai_tenant_gateway.tenant.TenantContext;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;

public class RedisTenantRateLimiter implements TenantRateLimiter {

    private static final String REQUEST_KEY = "rate:%s:requests";
    private static final String TOKEN_KEY = "rate:%s:tokens";
    private static final long LUA_ALLOWED = 0L;
    private static final long LUA_DENIED_REQUESTS = 1L;
    private static final long LUA_DENIED_TOKENS = 2L;

    private static final RedisScript<Long> CHECK_AND_CONSUME_SCRIPT = buildCheckAndConsumeScript();

    private final StringRedisTemplate redis;
    private final TenantGatewayConfigurationProperties properties;

    public RedisTenantRateLimiter(StringRedisTemplate redis,
                                  TenantGatewayConfigurationProperties properties) {
        this.redis = redis;
        this.properties = properties;
    }

    @Override
    public RateLimitDecision checkAndConsume(TenantContext context, int estimatedTokens) {
        String tenantId = context.tenantId();
        int tokensToConsume = Math.max(0, estimatedTokens);
        TenantGatewayConfigurationProperties.TenantLimits limits = properties.limitsFor(tenantId);

        String requestKey = REQUEST_KEY.formatted(tenantId);
        String tokenKey = TOKEN_KEY.formatted(tenantId);

        Long decision = redis.execute(
                CHECK_AND_CONSUME_SCRIPT,
                List.of(requestKey, tokenKey),
                Integer.toString(limits.getMaxRequestsPerMinute()),
                Integer.toString(limits.getMaxTokensPerDay()),
                Integer.toString(tokensToConsume),
                Long.toString(RateLimitDefaults.REQUEST_WINDOW_SECONDS),
                Long.toString(RateLimitDefaults.TOKEN_WINDOW_SECONDS)
        );

        if (decision == null) {
            throw new IllegalStateException("Redis rate limit script returned null");
        }
        if (decision == LUA_DENIED_REQUESTS) {
            return RateLimitDecision.deny(
                    RateLimitDefaults.RATE_LIMIT_EXCEEDED_REASON,
                    RateLimitDefaults.REQUEST_RETRY_AFTER_SECONDS
            );
        }
        if (decision == LUA_DENIED_TOKENS) {
            return RateLimitDecision.deny(
                    RateLimitDefaults.QUOTA_EXCEEDED_REASON,
                    RateLimitDefaults.TOKEN_RETRY_AFTER_SECONDS
            );
        }
        if (decision != LUA_ALLOWED) {
            throw new IllegalStateException("Unexpected Redis rate limit script result: " + decision);
        }

        return RateLimitDecision.permit();
    }

    @Override
    public TenantUsageSnapshot getUsage(String tenantId) {
        TenantGatewayConfigurationProperties.TenantLimits limits = properties.limitsFor(tenantId);

        String requestKey = REQUEST_KEY.formatted(tenantId);
        String tokenKey = TOKEN_KEY.formatted(tenantId);

        int requests = parseCount(redis.opsForValue().get(requestKey));
        int tokens = parseCount(redis.opsForValue().get(tokenKey));

        return new TenantUsageSnapshot(
                tenantId,
                requests,
                limits.getMaxRequestsPerMinute(),
                tokens,
                limits.getMaxTokensPerDay()
        );
    }

    private int parseCount(String value) {
        if (value == null) return 0;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static RedisScript<Long> buildCheckAndConsumeScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setResultType(Long.class);
        script.setScriptText("""
                local requestKey = KEYS[1]
                local tokenKey = KEYS[2]
                local requestLimit = tonumber(ARGV[1])
                local tokenLimit = tonumber(ARGV[2])
                local tokenIncrement = tonumber(ARGV[3])
                local requestWindowSeconds = tonumber(ARGV[4])
                local tokenWindowSeconds = tonumber(ARGV[5])

                local requestCount = redis.call('INCR', requestKey)
                if requestCount == 1 then
                    redis.call('EXPIRE', requestKey, requestWindowSeconds)
                end
                if requestCount > requestLimit then
                    return 1
                end

                if tokenIncrement > 0 then
                    local tokenCount = redis.call('INCRBY', tokenKey, tokenIncrement)
                    if tokenCount == tokenIncrement then
                        redis.call('EXPIRE', tokenKey, tokenWindowSeconds)
                    end
                    if tokenCount > tokenLimit then
                        redis.call('DECR', requestKey)
                        redis.call('DECRBY', tokenKey, tokenIncrement)
                        return 2
                    end
                end

                return 0
                """);
        return script;
    }
}
