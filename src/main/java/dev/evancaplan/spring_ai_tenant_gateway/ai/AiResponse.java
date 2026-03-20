package dev.evancaplan.spring_ai_tenant_gateway.ai;

public record AiResponse(String content, String tenantId, long latencyMs) {}
