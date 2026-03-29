package dev.evancaplan.spring_ai_tenant_gateway.controllers;

import dev.evancaplan.spring_ai_tenant_gateway.ai.AiRequest;
import dev.evancaplan.spring_ai_tenant_gateway.ai.AiResponse;
import dev.evancaplan.spring_ai_tenant_gateway.ai.TenantAwareAiService;
import dev.evancaplan.spring_ai_tenant_gateway.ratelimit.RateLimitDecision;
import dev.evancaplan.spring_ai_tenant_gateway.ratelimit.TenantRateLimiter;
import dev.evancaplan.spring_ai_tenant_gateway.tenant.TenantContext;
import dev.evancaplan.spring_ai_tenant_gateway.tenant.TenantContextHolder;
import dev.evancaplan.spring_ai_tenant_gateway.tenant.TenantMetricsRecorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/ai")
public class AiGatewayController {
    private static final Logger log = LoggerFactory.getLogger(AiGatewayController.class);
    private static final int CHARS_PER_ESTIMATED_TOKEN = 4;
    private static final String ROLE_USER = "user";
    private static final String ROLE_SYSTEM = "system";
    private static final String ROLE_ASSISTANT = "assistant";
    private static final String DEFAULT_MODEL = "gpt-4o-mini";
    private static final String OUTCOME_SUCCESS = "success";
    private static final String OUTCOME_ERROR = "error";

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
            log.error("No TenantContext in AiGatewayController.chat");
            return badRequest("Missing tenant context");
        }

        String validationError = validateRequest(request);
        if (validationError != null) {
            return badRequest(validationError);
        }

        int estimatedTokens = estimateTokens(request);

        RateLimitDecision decision = rateLimiter.checkAndConsume(tenantContext, estimatedTokens);

        if (!decision.allowed()) {
            metricsRecorder.recordRejection(tenantContext.tenantId(), decision.reason());
            return tooManyRequests(decision);
        }

        List<Message> messages = toMessages(request);
        String model = resolveModel(request.model());
        long startMs = System.currentTimeMillis();

        try {
            AiResponse aiResponse = aiService.chat(tenantContext, messages, estimatedTokens);
            long latencyMs = System.currentTimeMillis() - startMs;
            metricsRecorder.recordRequest(tenantContext.tenantId(), model, latencyMs, OUTCOME_SUCCESS);
            return ResponseEntity.ok(aiResponse);
        } catch (RuntimeException e) {
            long latencyMs = System.currentTimeMillis() - startMs;
            metricsRecorder.recordRequest(tenantContext.tenantId(), model, latencyMs, OUTCOME_ERROR);
            throw e;
        }
    }

    private int estimateTokens(AiRequest request) {
        return request.messages().stream()
                .mapToInt(message -> {
                    String content = message.getOrDefault("content", "");
                    if (content.isBlank()) {
                        return 0;
                    }
                    return Math.max(1, content.length() / CHARS_PER_ESTIMATED_TOKEN);
                })
                .sum();
    }

    private List<Message> toMessages(AiRequest request) {
        return request.messages().stream()
                .map(this::toMessage)
                .toList();
    }

    private Message toMessage(Map<String, String> message) {
        if (message == null) {
            return new UserMessage("");
        }
        String content = message.getOrDefault("content", "");
        String role = message.getOrDefault("role", ROLE_USER);

        return switch (role.toLowerCase(Locale.ROOT)) {
            case ROLE_SYSTEM -> new SystemMessage(content);
            case ROLE_ASSISTANT -> new AssistantMessage(content);
            case ROLE_USER -> new UserMessage(content);
            default -> new UserMessage(content);
        };
    }

    private ResponseEntity<Map<String, Object>> tooManyRequests(RateLimitDecision decision) {
        return ResponseEntity.status(429).body(Map.of(
                "error", decision.reason(),
                "retryAfterSeconds", decision.retryAfterSeconds()
        ));
    }

    private ResponseEntity<Map<String, String>> badRequest(String error) {
        return ResponseEntity.badRequest().body(Map.of("error", error));
    }

    private String validateRequest(AiRequest request) {
        if (request == null) {
            return "Request body is required";
        }
        if (request.messages() == null || request.messages().isEmpty()) {
            return "At least one message is required";
        }
        if (request.messages().stream().allMatch(m -> m == null || m.getOrDefault("content", "").isBlank())) {
            return "At least one non-empty message content is required";
        }
        return null;
    }

    private String resolveModel(String model) {
        if (model == null || model.isBlank()) {
            return DEFAULT_MODEL;
        }
        return model;
    }
}
