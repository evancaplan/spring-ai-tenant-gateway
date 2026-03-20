package dev.evancaplan.spring_ai_tenant_gateway.config;

import dev.evancaplan.spring_ai_tenant_gateway.tenant.AuthType;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

@ConfigurationProperties(prefix = "ai.tenant.gateway")
public class TenantGatewayConfigurationProperties {
    private int defaultMaxRequestsPerMinute = 10;
    private int defaultMaxTokensPerDay = 100_000;

    private String tenantIdJwtClaim = "tenant_id";
    private String teamIdJwtClaim = "team_id";

    private String tenantIdHeader = "X-Tenant-Id";
    private String teamIdHeader = "X-Team-Id";

    private AuthType authType = AuthType.HEADER;

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

    public String getTenantIdJwtClaim() {
        return tenantIdJwtClaim;
    }
    public void setTenantIdJwtClaim(String v) { this.tenantIdJwtClaim = v; }

    public String getTeamIdJwtClaim() { return teamIdJwtClaim; }
    public void setTeamIdJwtClaim(String v) { this.teamIdJwtClaim = v; }

    public TenantLimits limitsFor(String tenantId) {
        return tenants.getOrDefault(tenantId, new TenantLimits(
                defaultMaxRequestsPerMinute,
                defaultMaxTokensPerDay
        ));
    }

    public AuthType getAuthType() {
        return authType;
    }

    public void setAuthType(AuthType authType) {
        this.authType = authType;
    }

    public String getTenantIdHeader() {
        return tenantIdHeader;
    }

    public void setTenantIdHeader(String tenantIdHeader) {
        this.tenantIdHeader = tenantIdHeader;
    }

    public String getTeamIdHeader() {
        return teamIdHeader;
    }

    public void setTeamIdHeader(String teamIdHeader) {
        this.teamIdHeader = teamIdHeader;
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
