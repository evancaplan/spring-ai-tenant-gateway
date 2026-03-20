package dev.evancaplan.spring_ai_tenant_gateway.config;

import dev.evancaplan.spring_ai_tenant_gateway.tenant.TenantResolverFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TenantGatewayConfiguration {
    @Bean
    public FilterRegistrationBean<TenantResolverFilter> tenantResolverFilter() {
        FilterRegistrationBean<TenantResolverFilter> bean = new FilterRegistrationBean<>();
        bean.setFilter(new TenantResolverFilter());
        bean.addUrlPatterns("/api/*");
        bean.setOrder(1);
        return bean;
    }
}
