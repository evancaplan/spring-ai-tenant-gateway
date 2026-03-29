package dev.evancaplan.spring_ai_tenant_gateway.ai;

import dev.evancaplan.spring_ai_tenant_gateway.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TenantAwareAiService {
    private static final Logger log = LoggerFactory.getLogger(TenantAwareAiService.class);
    private static final String UNKNOWN = "unknown";

    private final ChatClient chatClient;

    public TenantAwareAiService(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    public AiResponse chat(TenantContext context, List<Message> messages, int estimatedTokens) {
        TenantContext resolvedContext = resolveContext(context);
        String tenantId = resolvedContext != null ? resolvedContext.tenantId() : UNKNOWN;
        String teamId = resolvedContext != null ? resolvedContext.teamId() : UNKNOWN;

        long start = System.currentTimeMillis();
        long latencyMs;
        String outcome = "success";
        String content;

        try {
            content = chatClient.prompt()
                    .messages(messages)
                    .call()
                    .content();
        } catch (Exception e) {
            outcome = "error";
            log.error("LLM call failed for tenant={} error={}", tenantId, e.getMessage());
            throw e;
        } finally {
            latencyMs = System.currentTimeMillis() - start;
            ChatLogEntry entry = new ChatLogEntry(
                    tenantId,
                    teamId,
                    "gpt-4o-mini",
                    estimatedTokens,
                    latencyMs,
                    outcome
            );
            log.info("ai_chat tenantId={} teamId={} model={} estimatedTokens={} latencyMs={} outcome={}",
                    entry.tenantId(),
                    entry.teamId(),
                    entry.model(),
                    entry.estimatedTokens(),
                    entry.latencyMs(),
                    entry.outcome()
            );
        }

        return new AiResponse(content, tenantId, latencyMs);
    }

    private TenantContext resolveContext(TenantContext context) {
        if (context != null) {
            return context;
        }
        return dev.evancaplan.spring_ai_tenant_gateway.tenant.TenantContextHolder.get();
    }

}
