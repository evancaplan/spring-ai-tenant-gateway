package dev.evancaplan.spring_ai_tenant_gateway.ratelimit;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class RateLimitDecisionTest {

    @Test
    void permit_CreatesPermitDecision() {
        RateLimitDecision decision = RateLimitDecision.permit();
        assertThat(decision.allowed()).isTrue();
        assertThat(decision.reason()).isNull();
        assertThat(decision.retryAfterSeconds()).isZero();
    }

    @Test
    void deny_CreatesDenyDecision() {
        RateLimitDecision decision = RateLimitDecision.deny("too_many_requests", 60);
        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reason()).isEqualTo("too_many_requests");
        assertThat(decision.retryAfterSeconds()).isEqualTo(60);
    }
}
