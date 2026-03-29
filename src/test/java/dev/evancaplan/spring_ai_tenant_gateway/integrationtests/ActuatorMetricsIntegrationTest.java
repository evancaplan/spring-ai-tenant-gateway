package dev.evancaplan.spring_ai_tenant_gateway.integrationtests;

import dev.evancaplan.spring_ai_tenant_gateway.SpringAiTenantGatewayApplication;
import dev.evancaplan.spring_ai_tenant_gateway.ai.AiRequest;
import dev.evancaplan.spring_ai_tenant_gateway.ai.AiResponse;
import dev.evancaplan.spring_ai_tenant_gateway.ai.TenantAwareAiService;
import dev.evancaplan.spring_ai_tenant_gateway.tenant.TenantContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
    classes = SpringAiTenantGatewayApplication.class,
    properties = {
        "ai.tenant.gateway.rate-limit-strategy=IN_MEMORY",
        "spring.ai.openai.api-key=test",
        "spring.ai.openai.chat.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration",
        "management.endpoints.web.exposure.include=*"
    }
)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ActuatorMetricsIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TenantAwareAiService aiService;

    @Test
    void testMetricsAfterSuccessfulAndRejectedRequests() throws Exception {
        AiRequest request = new AiRequest(List.of(Map.of("role", "user", "content", "Hello")), "gpt-4o-mini");
        
        when(aiService.chat(any(TenantContext.class), anyList(), anyInt()))
                .thenReturn(new AiResponse("Hello", "tenant-metrics", 50L));

        // 1. Successful request
        mockMvc.perform(post("/api/v1/ai/chat")
                        .header("X-Tenant-Id", "tenant-metrics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        // Verify ai.requests.total
        mockMvc.perform(get("/actuator/metrics/ai.requests.total")
                        .param("tag", "tenantId:tenant-metrics")
                        .param("tag", "model:gpt-4o-mini")
                        .param("tag", "outcome:success"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.measurements[0].value").value(1.0));

        // Verify ai.request.latency
        mockMvc.perform(get("/actuator/metrics/ai.request.latency")
                        .param("tag", "tenantId:tenant-metrics")
                        .param("tag", "model:gpt-4o-mini"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.measurements[0].value").value(1.0)) // count
                .andExpect(jsonPath("$.measurements[1].value").isNumber()); // total time

        // 2. Rejected request (trigger rate limit by exhausting it)
        // Default is 10 requests/minute. We already made 1 successful request above,
        // so make 9 more successful requests and then verify the next one is rejected.
        for (int i = 0; i < 9; i++) {
            mockMvc.perform(post("/api/v1/ai/chat")
                            .header("X-Tenant-Id", "tenant-metrics")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)));
        }

        // The 11th request should be rejected
        mockMvc.perform(post("/api/v1/ai/chat")
                        .header("X-Tenant-Id", "tenant-metrics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().is(429));

        // Verify ai.requests.rejected
        mockMvc.perform(get("/actuator/metrics/ai.requests.rejected")
                        .param("tag", "tenantId:tenant-metrics")
                        .param("tag", "reason:rate_limit_exceeded: too many requests per minute"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.measurements[0].value").value(1.0));
    }
}
