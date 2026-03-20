package dev.evancaplan.spring_ai_tenant_gateway.controllers;

import dev.evancaplan.spring_ai_tenant_gateway.ratelimit.TenantRateLimiter;
import dev.evancaplan.spring_ai_tenant_gateway.ratelimit.TenantUsageSnapshot;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tenants")
public class TenantUsageController {

    private final TenantRateLimiter rateLimiter;

    public TenantUsageController(TenantRateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    @GetMapping("/{tenantId}/usage")
    public ResponseEntity<TenantUsageSnapshot> getUsage(@PathVariable String tenantId) {
        return ResponseEntity.ok(rateLimiter.getUsage(tenantId));
    }
}

