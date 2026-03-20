package dev.evancaplan.spring_ai_tenant_gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

@ConfigurationProperties(prefix = "ai.tenant.gateway")
public class TenantGatewayConfigurationProperties {
    private int defaultMaxRequestsPerMinute = 10;
    private int defaultMaxTokensPerDay = 100_000;
    private Map<String, TenantLimits> tenants = new HashMap<>();

    public int getDefaultMaxRequestsPerMinute() {
        return defaultMaxRequestsPerMinute;
    }

    public void setDefaultMaxRequestsPerMinute(int v) {
        this.defaultMaxRequestsPerMinute = v;
    }

    public int getDefaultMaxTokensPerDay() {
        return defaultMaxTokensPerDay;
    }

    public void setDefaultMaxTokensPerDay(int v) {
        this.defaultMaxTokensPerDay = v;
    }

    public Map<String, TenantLimits> getTenants() {
        return tenants;
    }

    public void setTenants(Map<String, TenantLimits> tenants) {
        this.tenants = tenants;
    }

    public TenantLimits limitsFor(String tenantId) {
        return tenants.getOrDefault(tenantId, new TenantLimits(
                defaultMaxRequestsPerMinute,
                defaultMaxTokensPerDay
        ));
    }

    public static class TenantLimits {
        private int maxRequestsPerMinute;
        private int maxTokensPerDay;

        public TenantLimits() {
        }

        public TenantLimits(int maxRequestsPerMinute, int maxTokensPerDay) {
            this.maxRequestsPerMinute = maxRequestsPerMinute;
            this.maxTokensPerDay = maxTokensPerDay;
        }

        public int getMaxRequestsPerMinute() {
            return maxRequestsPerMinute;
        }

        public void setMaxRequestsPerMinute(int v) {
            this.maxRequestsPerMinute = v;
        }

        public int getMaxTokensPerDay() {
            return maxTokensPerDay;
        }

        public void setMaxTokensPerDay(int v) {
            this.maxTokensPerDay = v;
        }
    }
}
