package dev.evancaplan.spring_ai_tenant_gateway.config;

import dev.evancaplan.spring_ai_tenant_gateway.tenant.TenantResolverFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {
    private final TenantGatewayConfigurationProperties properties;

    public SecurityConfig(TenantGatewayConfigurationProperties properties) {
        this.properties = properties;
    }

    @Bean
    public TenantResolverFilter tenantResolverFilter() {
        return new TenantResolverFilter(properties);
    }

    @Bean
    @Order(2)
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable());

        switch (properties.getAuthType()) {
            case JWT -> http
                    .authorizeHttpRequests(auth -> auth
                            .requestMatchers("/actuator/**").permitAll()
                            .anyRequest().authenticated()
                    )
                    .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));

            case HEADER -> http
                    .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        }

        http.addFilterAfter(
                tenantResolverFilter(),
                UsernamePasswordAuthenticationFilter.class
        );

        return http.build();
    }
}
