package dev.evancaplan.spring_ai_tenant_gateway.integrationtests.controllers;

import dev.evancaplan.spring_ai_tenant_gateway.integrationtests.BaseIntegrationTest;
import dev.evancaplan.spring_ai_tenant_gateway.ratelimit.TenantRateLimiter;
import dev.evancaplan.spring_ai_tenant_gateway.tenant.TenantContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:8080/oauth2/jwks",
        "spring.ai.openai.api-key=test",
        "spring.ai.openai.chat.enabled=false",
        "spring.data.redis.repositories.enabled=false"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TenantUsageControllerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TenantRateLimiter caffeineTenantRateLimiter;

    @MockitoBean
    private StringRedisTemplate redisTemplate;

    @Test
    void getUsage_ReturnsCorrectSnapshotAfterRequests() throws Exception {
        String tenantId = "usage-tenant";
        TenantContext context = new TenantContext(tenantId, "team-1");
        
        // Make some requests directly through rate limiter to simulate usage
        caffeineTenantRateLimiter.checkAndConsume(context, 100);
        caffeineTenantRateLimiter.checkAndConsume(context, 200);

        mockMvc.perform(get("/api/v1/tenants/{tenantId}/usage", tenantId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value(tenantId))
                .andExpect(jsonPath("$.requestsThisMinute").value(2))
                .andExpect(jsonPath("$.tokensToday").value(300));
    }
}
