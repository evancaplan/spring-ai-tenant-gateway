package dev.evancaplan.spring_ai_tenant_gateway.integrationtests.controllers;

import dev.evancaplan.spring_ai_tenant_gateway.SpringAiTenantGatewayApplication;
import dev.evancaplan.spring_ai_tenant_gateway.ai.AiRequest;
import dev.evancaplan.spring_ai_tenant_gateway.ai.AiResponse;
import dev.evancaplan.spring_ai_tenant_gateway.ai.TenantAwareAiService;
import dev.evancaplan.spring_ai_tenant_gateway.integrationtests.BaseIntegrationTest;
import dev.evancaplan.spring_ai_tenant_gateway.ratelimit.RedisTenantRateLimiter;
import dev.evancaplan.spring_ai_tenant_gateway.ratelimit.TenantRateLimiter;
import dev.evancaplan.spring_ai_tenant_gateway.tenant.TenantContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

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
        "ai.tenant.gateway.rate-limit-strategy=REDIS",
        "spring.ai.openai.api-key=test",
        "spring.ai.openai.chat.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration"
    }
)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class RedisAiGatewayIntegrationTest extends BaseIntegrationTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @org.springframework.test.context.DynamicPropertySource
    static void redisProperties(org.springframework.test.context.DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TenantAwareAiService aiService;

    @Autowired
    private TenantRateLimiter rateLimiter;

    @Test
    void shouldLoadRedisRateLimiter() {
        assertThat(rateLimiter).isInstanceOf(RedisTenantRateLimiter.class);
    }

    @Test
    void chatWithRedisStrategy() throws Exception {
        AiRequest request = new AiRequest(List.of(Map.of("role", "user", "content", "Hello")),
                "gpt-4o-mini");


        when(aiService.chat(any(TenantContext.class), anyList(), anyInt()))
                .thenReturn(new AiResponse("Hello from Redis!", "tenant-redis", 100));

        mockMvc.perform(post("/api/v1/ai/chat")
                        .header("X-Tenant-Id", "tenant-redis")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("Hello from Redis!"))
                .andExpect(jsonPath("$.tenantId").value("tenant-redis"));
    }
}
