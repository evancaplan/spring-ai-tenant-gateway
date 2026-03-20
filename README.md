# spring-ai-tenant-gateway

A Spring Boot starter that adds per-tenant rate limiting, quota enforcement, and observability to any Spring AI-powered API.

Built for platform and infrastructure teams who need to expose LLM-backed endpoints to multiple tenants with configurable limits, structured logging, and Micrometer metrics out of the box.

---

## Features

- Multi-tenant request resolution via JWT claims or headers
- Per-tenant rate limiting (requests per minute)
- Per-tenant token quota (tokens per day)
- YAML-configurable limits with per-tenant overrides
- Spring AI provider abstraction (OpenAI, Bedrock, and others)
- Structured logging per request (tenant, model, latency, outcome)
- Micrometer metrics tagged by tenant, model, and outcome
- Usage snapshot endpoint per tenant
- 429 responses with retry hints on limit exceeded

---

## Quick Start

### 1. Add the dependency

```xml
<dependency>
    <groupId>dev.evancaplan</groupId>
    <artifactId>spring-ai-tenant-gateway</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</dependency>
```

### 2. Configure limits in `application.properties`

```properties
# Default limits for all tenants
ai.tenant.gateway.default-max-requests-per-minute=10
ai.tenant.gateway.default-max-tokens-per-day=100000

# Per-tenant overrides
ai.tenant.gateway.tenants.acme.max-requests-per-minute=20
ai.tenant.gateway.tenants.acme.max-tokens-per-day=200000

ai.tenant.gateway.tenants.trial.max-requests-per-minute=3
ai.tenant.gateway.tenants.trial.max-tokens-per-day=10000

# Spring AI provider
spring.ai.openai.api-key=${OPENAI_API_KEY}
spring.ai.openai.chat.options.model=gpt-4o-mini
```

### 3. Identify your tenant

Pass tenant identity via headers (JWT support coming in v2):

```
X-Tenant-Id: acme
X-Team-Id: engineering
```

---

## API

### `POST /api/v1/ai/chat`

Send a chat request on behalf of a tenant.

**Request**
```json
{
  "messages": [
    { "role": "user", "content": "What is a large language model?" }
  ],
  "model": "gpt-4o-mini"
}
```

**Response 200**
```json
{
  "content": "A large language model is...",
  "tenantId": "acme",
  "latencyMs": 1903
}
```

**Response 429 (rate limit exceeded)**
```json
{
  "error": "rate_limit_exceeded: too many requests per minute",
  "retryAfterSeconds": 30
}
```

---

### `GET /api/v1/tenants/{tenantId}/usage`

Get current usage snapshot for a tenant.

**Response 200**
```json
{
  "tenantId": "acme",
  "requestsThisMinute": 3,
  "maxRequestsPerMinute": 20,
  "tokensToday": 450,
  "maxTokensPerDay": 200000
}
```

---

## Observability

### Metrics (Micrometer)

| Metric | Tags | Description |
|---|---|---|
| `ai.requests.total` | `tenantId`, `model`, `outcome` | Total LLM requests |
| `ai.request.latency` | `tenantId`, `model` | Request latency histogram |
| `ai.requests.rejected` | `tenantId`, `reason` | Rejected requests |

Expose via Actuator:

```properties
management.endpoints.web.exposure.include=health,metrics
```

Then query:

```
GET /actuator/metrics/ai.requests.total
GET /actuator/metrics/ai.request.latency
GET /actuator/metrics/ai.requests.rejected
```

### Structured Logs

Every request emits a structured log line:

```
INFO TenantAwareAiService - ai_chat tenantId=acme teamId=engineering model=gpt-4o-mini estimatedTokens=11 latencyMs=1673 outcome=success
```

---

## Architecture

```
HTTP Request
    │
    ▼
TenantResolverFilter       (resolves tenantId + teamId from headers/JWT)
    │
    ▼
AiGatewayController        (validates tenant context)
    │
    ▼
InMemoryTenantRateLimiter  (checks req/min + tokens/day limits)
    │
    ├── 429 → TenantMetricsRecorder (records rejection metric)
    │
    ▼
TenantAwareAiService       (calls Spring AI ChatClient)
    │
    ▼
TenantMetricsRecorder      (records latency + request metrics)
    │
    ▼
Structured log + response
```

---

## Roadmap

- [ ] JWT-based tenant resolution
- [ ] Redis-backed rate limiting for multi-instance deployments
- [ ] Cost-based quotas ($ per day per tenant)
- [ ] Per-tenant model restrictions
- [ ] Separate `-starter` and `-example` Maven modules
- [ ] Spring Boot autoconfiguration packaging

---

## Running Locally

```bash
git clone https://github.com/evancaplan/spring-ai-tenant-gateway.git
cd spring-ai-tenant-gateway
export OPENAI_API_KEY=your_key_here
./mvnw spring-boot:run
```

---

## Contributing

PRs welcome. Open an issue first for large changes.

---

## License

Apache 2.0
