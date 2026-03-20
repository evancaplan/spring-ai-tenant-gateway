package dev.evancaplan.spring_ai_tenant_gateway.ai;

import java.util.List;
import java.util.Map;

public record AiRequest(List<Map<String, String>> messages, String model) {}

