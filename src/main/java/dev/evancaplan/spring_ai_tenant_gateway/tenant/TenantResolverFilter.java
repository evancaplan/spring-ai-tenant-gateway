package dev.evancaplan.spring_ai_tenant_gateway.tenant;

import dev.evancaplan.spring_ai_tenant_gateway.config.TenantGatewayConfigurationProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class TenantResolverFilter extends OncePerRequestFilter {
    private final TenantGatewayConfigurationProperties properties;

    public TenantResolverFilter(TenantGatewayConfigurationProperties properties) {
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        try {
            TenantContext context = switch (properties.getAuthType()) {
                case JWT -> resolveFromJwt();
                case HEADER -> resolveFromHeaders(request);
            };

            if (context != null) {
                TenantContextHolder.set(context);
            }

            filterChain.doFilter(request, response);
        } finally {
            TenantContextHolder.clear();
        }
    }

    private TenantContext resolveFromJwt() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Jwt jwt)) {
            return null;
        }

        String tenantId = jwt.getClaimAsString(properties.getTenantIdJwtClaim());
        String teamId = jwt.getClaimAsString(properties.getTeamIdJwtClaim());

        if (tenantId == null) return null;
        return new TenantContext(tenantId, teamId);
    }

    private TenantContext resolveFromHeaders(HttpServletRequest request) {
        String tenantId = request.getHeader(properties.getTenantIdHeader());
        String teamId = request.getHeader(properties.getTeamIdHeader());

        if (tenantId == null) return null;
        return new TenantContext(tenantId, teamId);
    }
}
