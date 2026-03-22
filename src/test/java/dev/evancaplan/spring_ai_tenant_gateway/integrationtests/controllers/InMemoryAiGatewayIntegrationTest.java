package dev.evancaplan.spring_ai_tenant_gateway.integrationtests.controllers;

import dev.evancaplan.spring_ai_tenant_gateway.SpringAiTenantGatewayApplication;
import dev.evancaplan.spring_ai_tenant_gateway.ai.AiRequest;
import dev.evancaplan.spring_ai_tenant_gateway.ai.AiResponse;
import dev.evancaplan.spring_ai_tenant_gateway.ai.TenantAwareAiService;
import dev.evancaplan.spring_ai_tenant_gateway.integrationtests.BaseIntegrationTest;
import dev.evancaplan.spring_ai_tenant_gateway.ratelimit.InMemoryTenantRateLimiter;
import dev.evancaplan.spring_ai_tenant_gateway.ratelimit.TenantRateLimiter;
import dev.evancaplan.spring_ai_tenant_gateway.tenant.TenantContext;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.ai.chat.messages.Message;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        classes = SpringAiTenantGatewayApplication.class,
        properties = {
                "ai.tenant.gateway.rate-limit-strategy=IN_MEMORY",
                "spring.ai.openai.api-key=test",
                "spring.ai.openai.chat.enabled=false",
                "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration"
        }
)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InMemoryAiGatewayIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TenantAwareAiService aiService;

    @Autowired
    private TenantRateLimiter rateLimiter;

    @MockitoBean
    private StringRedisTemplate redisTemplate;

    @Test
    void shouldLoadInMemoryRateLimiter() {
        assertThat(rateLimiter).isInstanceOf(InMemoryTenantRateLimiter.class);
    }

    @Test
    void chat_WithInMemoryStrategy() throws Exception {
        AiRequest request = new AiRequest(List.of(Map.of("role", "user", "content", "Hello")),
                "gpt-4o-mini");

        when(aiService.chat(any(TenantContext.class), ArgumentMatchers.<List<Message>>any(), anyInt()))
                .thenReturn(new AiResponse("Hello from In-Memory!", "tenant-in-memory", 100));

        mockMvc.perform(post("/api/v1/ai/chat")
                        .header("X-Tenant-Id", "tenant-in-memory")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("Hello from In-Memory!"))
                .andExpect(jsonPath("$.tenantId").value("tenant-in-memory"));
    }

    @Test
    void chat_WithMissingMessages_ReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/ai/chat")
                        .header("X-Tenant-Id", "tenant-in-memory")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"model\":\"gpt-4o-mini\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("At least one message is required"));
    }

    @Test
    void chat_WithBlankMessages_ReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/ai/chat")
                        .header("X-Tenant-Id", "tenant-in-memory")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"messages\":[{\"role\":\"user\",\"content\":\"   \"}],\"model\":\"gpt-4o-mini\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("At least one non-empty message content is required"));
    }
}
