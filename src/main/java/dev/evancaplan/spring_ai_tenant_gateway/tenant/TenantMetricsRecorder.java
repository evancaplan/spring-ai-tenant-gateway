package dev.evancaplan.spring_ai_tenant_gateway.tenant;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class TenantMetricsRecorder {

    private final MeterRegistry registry;

    public TenantMetricsRecorder(MeterRegistry registry) {
        this.registry = registry;
    }

    public void recordRequest(String tenantId, String model, long latencyMs, String outcome) {
        // counter: total requests per tenant + outcome
        registry.counter("ai.requests.total",
                "tenantId", tenantId,
                "model", model,
                "outcome", outcome
        ).increment();

        // timer: latency histogram per tenant
        Timer.builder("ai.request.latency")
                .tag("tenantId", tenantId)
                .tag("model", model)
                .register(registry)
                .record(latencyMs, TimeUnit.MILLISECONDS);
    }

    public void recordRejection(String tenantId, String reason) {
        registry.counter("ai.requests.rejected",
                "tenantId", tenantId,
                "reason", reason
        ).increment();
    }
}
