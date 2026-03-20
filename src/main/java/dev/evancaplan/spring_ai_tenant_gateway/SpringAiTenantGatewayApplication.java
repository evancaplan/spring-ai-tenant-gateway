package dev.evancaplan.spring_ai_tenant_gateway;

import dev.evancaplan.spring_ai_tenant_gateway.config.TenantGatewayConfigurationProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(TenantGatewayConfigurationProperties.class)
public class SpringAiTenantGatewayApplication {

	public static void main(String[] args) {
		SpringApplication.run(SpringAiTenantGatewayApplication.class, args);
	}

}
