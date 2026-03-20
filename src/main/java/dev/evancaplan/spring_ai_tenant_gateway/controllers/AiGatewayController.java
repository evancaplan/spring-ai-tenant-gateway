package dev.evancaplan.spring_ai_tenant_gateway.controllers;

import dev.evancaplan.spring_ai_tenant_gateway.ai.AiRequest;
import dev.evancaplan.spring_ai_tenant_gateway.ai.AiResponse;
import dev.evancaplan.spring_ai_tenant_gateway.ai.TenantAwareAiService;
import dev.evancaplan.spring_ai_tenant_gateway.ratelimit.RateLimitDecision;
import dev.evancaplan.spring_ai_tenant_gateway.ratelimit.TenantRateLimiter;
import dev.evancaplan.spring_ai_tenant_gateway.tenant.TenantContext;
import dev.evancaplan.spring_ai_tenant_gateway.tenant.TenantContextHolder;
import dev.evancaplan.spring_ai_tenant_gateway.tenant.TenantMetricsRecorder;
import org.springframework.ai.chat.messages.Message;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/ai")
public class AiGatewayController {

    private final TenantRateLimiter rateLimiter;
    private final TenantAwareAiService aiService;
    private final TenantMetricsRecorder metricsRecorder;

    public AiGatewayController(TenantRateLimiter rateLimiter,
                               TenantAwareAiService aiService,
                               TenantMetricsRecorder metricsRecorder) {
        this.rateLimiter = rateLimiter;
        this.aiService = aiService;
        this.metricsRecorder = metricsRecorder;
    }

    @PostMapping("/chat")
    public ResponseEntity<?> chat(@RequestBody AiRequest request) {
        TenantContext tenantContext = TenantContextHolder.get();

        if (tenantContext == null) {
            return ResponseEntity.badRequest().body("Missing tenant context");
        }

        int estimatedTokens = request.messages().stream()
                .mapToInt(m -> m.getOrDefault("content", "").length() / 4)
                .sum();

        RateLimitDecision decision = rateLimiter.checkAndConsume(tenantContext, estimatedTokens);

        if (!decision.allowed()) {
            metricsRecorder.recordRejection(tenantContext.tenantId(), decision.reason());
            return ResponseEntity.status(429).body(Map.of(
                    "error", decision.reason(),
                    "retryAfterSeconds", decision.retryAfterSeconds()
            ));
        }

        List<Message> messages = request.messages().stream()
                .map(m -> (Message) new org.springframework.ai.chat.messages.UserMessage(
                        m.getOrDefault("content", "")))
                .toList();

        AiResponse aiResponse = aiService.chat(tenantContext, messages, estimatedTokens);

        return ResponseEntity.ok(aiResponse);
    }
}
