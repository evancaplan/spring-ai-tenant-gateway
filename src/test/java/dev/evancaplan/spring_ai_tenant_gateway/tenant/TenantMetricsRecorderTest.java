package dev.evancaplan.spring_ai_tenant_gateway.tenant;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class TenantMetricsRecorderTest {

    private MeterRegistry registry;
    private TenantMetricsRecorder recorder;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        recorder = new TenantMetricsRecorder(registry);
    }

    @Test
    void recordRequest_shouldIncrementTotalCounter() {
        recorder.recordRequest("tenant-1", "gpt-4", 150L, "success");

        assertThat(registry.find("ai.requests.total")
                .tag("tenantId", "tenant-1")
                .tag("model", "gpt-4")
                .tag("outcome", "success")
                .counter().count()).isEqualTo(1.0);
    }

    @Test
    void recordRequest_shouldRecordLatency() {
        recorder.recordRequest("tenant-1", "gpt-4", 150L, "success");

        assertThat(registry.find("ai.request.latency")
                .tag("tenantId", "tenant-1")
                .tag("model", "gpt-4")
                .timer().totalTime(TimeUnit.MILLISECONDS)).isEqualTo(150.0);
    }

    @Test
    void recordRejection_shouldIncrementRejectedCounter() {
        recorder.recordRejection("tenant-1", "rate_limit_exceeded");

        assertThat(registry.find("ai.requests.rejected")
                .tag("tenantId", "tenant-1")
                .tag("reason", "rate_limit_exceeded")
                .counter().count()).isEqualTo(1.0);
    }
}
