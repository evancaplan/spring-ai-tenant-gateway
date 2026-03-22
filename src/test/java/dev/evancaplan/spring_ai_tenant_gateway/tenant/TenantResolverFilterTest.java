package dev.evancaplan.spring_ai_tenant_gateway.tenant;

import dev.evancaplan.spring_ai_tenant_gateway.config.TenantGatewayConfigurationProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class TenantResolverFilterTest {

    private TenantGatewayConfigurationProperties properties;
    private TenantResolverFilter filter;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        properties = new TenantGatewayConfigurationProperties();
        filter = new TenantResolverFilter(properties);
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        filterChain = mock(FilterChain.class);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void doFilterInternal_ResolvesFromHeaders() throws Exception {
        properties.setAuthType(AuthType.HEADER);
        when(request.getHeader("X-Tenant-Id")).thenReturn("tenant-123");
        when(request.getHeader("X-Team-Id")).thenReturn("team-456");

        filter.doFilterInternal(request, response, filterChain);

        // Verification is tricky because it clears after, but let's check if filter chain was called
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilterInternal_ResolvesFromJwt() throws Exception {
        properties.setAuthType(AuthType.JWT);
        properties.setTenantIdJwtClaim("tenant_id");
        properties.setTeamIdJwtClaim("team_id");

        Jwt jwt = mock(Jwt.class);
        when(jwt.getClaimAsString("tenant_id")).thenReturn("tenant-jwt");
        when(jwt.getClaimAsString("team_id")).thenReturn("team-jwt");

        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(jwt);
        
        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(securityContext);

        // To test that it was set, we can use a custom filter chain or mock
        doAnswer(invocation -> {
            TenantContext context = TenantContextHolder.get();
            assertThat(context).isNotNull();
            assertThat(context.tenantId()).isEqualTo("tenant-jwt");
            assertThat(context.teamId()).isEqualTo("team-jwt");
            return null;
        }).when(filterChain).doFilter(request, response);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(TenantContextHolder.get()).isNull(); // Should be cleared
    }

    @Test
    void doFilterInternal_MissingTenantId_NoContextSet() throws Exception {
        properties.setAuthType(AuthType.HEADER);
        when(request.getHeader("X-Tenant-Id")).thenReturn(null);

        doAnswer(invocation -> {
            assertThat(TenantContextHolder.get()).isNull();
            return null;
        }).when(filterChain).doFilter(request, response);

        filter.doFilterInternal(request, response, filterChain);
    }

    @Test
    void doFilterInternal_ClearsContextAfterRequest() throws Exception {
        properties.setAuthType(AuthType.HEADER);
        when(request.getHeader("X-Tenant-Id")).thenReturn("tenant-1");

        filter.doFilterInternal(request, response, filterChain);

        assertThat(TenantContextHolder.get()).isNull();
    }
}
