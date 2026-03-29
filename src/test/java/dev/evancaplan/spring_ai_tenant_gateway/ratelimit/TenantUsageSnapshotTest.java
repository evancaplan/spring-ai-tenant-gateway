package dev.evancaplan.spring_ai_tenant_gateway.ratelimit;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class TenantUsageSnapshotTest {

    @Test
    void record_ConstructionAndAccessors() {
        TenantUsageSnapshot snapshot = new TenantUsageSnapshot(
            "tenant-1", 5, 10, 500, 1000
        );
        
        assertThat(snapshot.tenantId()).isEqualTo("tenant-1");
        assertThat(snapshot.requestsThisMinute()).isEqualTo(5);
        assertThat(snapshot.maxRequestsPerMinute()).isEqualTo(10);
        assertThat(snapshot.tokensToday()).isEqualTo(500);
        assertThat(snapshot.maxTokensPerDay()).isEqualTo(1000);
    }
}
