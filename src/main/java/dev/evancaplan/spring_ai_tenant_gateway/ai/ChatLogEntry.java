package dev.evancaplan.spring_ai_tenant_gateway.ai;

public record ChatLogEntry(
        String tenantId,
        String teamId,
        String model,
        int estimatedTokens,
        long latencyMs,
        String outcome
) {}
