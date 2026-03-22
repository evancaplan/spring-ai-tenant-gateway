package dev.evancaplan.spring_ai_tenant_gateway.integrationtests.auth;

import dev.evancaplan.spring_ai_tenant_gateway.ai.AiResponse;
import dev.evancaplan.spring_ai_tenant_gateway.ai.TenantAwareAiService;
import dev.evancaplan.spring_ai_tenant_gateway.config.TenantGatewayConfigurationProperties;
import dev.evancaplan.spring_ai_tenant_gateway.tenant.AuthType;
import dev.evancaplan.spring_ai_tenant_gateway.tenant.TenantContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "ai.tenant.gateway.auth-type=JWT",
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:8080/oauth2/jwks",
        "spring.ai.openai.api-key=test",
        "spring.ai.openai.chat.enabled=false",
        "spring.data.redis.repositories.enabled=false"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class JwtAuthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TenantAwareAiService aiService;

    @Autowired
    private TenantGatewayConfigurationProperties properties;

    @MockitoBean
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    void setUp() {
        properties.setAuthType(AuthType.JWT);
        properties.setTenantIdJwtClaim("tenant_id");
        properties.setTeamIdJwtClaim("team_id");
    }

    // Proves valid JWT claims resolve tenant context and allow authenticated chat requests.
    @Test
    void jwtMode_ResolvesTenantFromClaims() throws Exception {
        String jsonRequest = """
                {
                    "messages": [{"role": "user", "content": "Hello"}],
                    "model": "gpt-4o-mini"
                }
                """;

        when(aiService.chat(any(TenantContext.class), anyList(), anyInt()))
                .thenReturn(new AiResponse("JWT Response", "tenant-jwt", 100));

        mockMvc.perform(post("/api/v1/ai/chat")
                        .with(jwt().jwt(jwt -> jwt.claim("tenant_id", "tenant-jwt").claim("team_id", "team-jwt")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("JWT Response"))
                .andExpect(jsonPath("$.tenantId").value("tenant-jwt"));
    }

    // Proves JWT mode rejects unauthenticated requests with no Bearer token.
    @Test
    void jwtMode_MissingBearerToken_Returns401() throws Exception {
        mockMvc.perform(post("/api/v1/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "messages": [{"role":"user","content":"Hello"}],
                                  "model": "gpt-4o-mini"
                                }
                                """))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(aiService);
    }

    // Proves malformed/invalid JWTs are rejected before controller logic executes.
    @Test
    void jwtMode_InvalidJwtToken_Returns401() throws Exception {
        mockMvc.perform(post("/api/v1/ai/chat")
                        .header("Authorization", "Bearer not-a-valid-jwt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "messages": [{"role":"user","content":"Hello"}],
                                  "model": "gpt-4o-mini"
                                }
                                """))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(aiService);
    }
}
