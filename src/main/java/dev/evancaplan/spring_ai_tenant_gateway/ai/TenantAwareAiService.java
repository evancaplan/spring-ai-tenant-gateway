package dev.evancaplan.spring_ai_tenant_gateway.ai;

import dev.evancaplan.spring_ai_tenant_gateway.tenant.TenantContext;
import dev.evancaplan.spring_ai_tenant_gateway.tenant.TenantMetricsRecorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TenantAwareAiService {
    private static final Logger log = LoggerFactory.getLogger(TenantAwareAiService.class);

    private final ChatClient chatClient;

    private final TenantMetricsRecorder metricsRecorder;

    public TenantAwareAiService(ChatClient.Builder builder, TenantMetricsRecorder metricsRecorder) {
        this.chatClient = builder.build();
        this.metricsRecorder = metricsRecorder;
    }

    public AiResponse chat(TenantContext context, List<Message> messages, int estimatedTokens) {
        long start = System.currentTimeMillis();
        String outcome = "success";
        String content = null;

        try {
            content = chatClient.prompt()
                    .messages(messages)
                    .call()
                    .content();
        } catch (Exception e) {
            outcome = "error";
            log.error("LLM call failed for tenant={} error={}", context.tenantId(), e.getMessage());
            throw e;
        } finally {
            long latencyMs = System.currentTimeMillis() - start;
            ChatLogEntry entry = new ChatLogEntry(
                    context.tenantId(),
                    context.teamId(),
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

            metricsRecorder.recordRequest(
                    context.tenantId(),
                    "gpt-4o-mini",
                    latencyMs,
                    outcome
            );
        }

        return new AiResponse(content, context.tenantId(), System.currentTimeMillis() - start);
    }


}
