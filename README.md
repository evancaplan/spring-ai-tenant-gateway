# spring-ai-tenant-gateway

Spring Boot gateway service for multi-tenant AI chat APIs with:

- Tenant resolution via `HEADER` or `JWT`
- Per-tenant request-rate and token-quota enforcement
- Pluggable rate limiting backend (`IN_MEMORY`, `CAFFEINE`, `REDIS`)
- Actuator + Micrometer metrics
- Tenant usage endpoint

## What It Exposes

- `POST /api/v1/ai/chat`
- `GET /api/v1/tenants/{tenantId}/usage`
- Actuator metrics (when exposed): `/actuator/metrics/**`

## Core Features

- Tenant context from headers or JWT claims
- Request validation and token estimation
- Per-tenant limits:
  - requests/minute
  - tokens/day
- 429 responses with retry hints:
  - `rate_limit_exceeded: too many requests per minute`
  - `quota_exceeded: daily token limit reached`
- Structured logs with tenant/model/latency/outcome
- Metrics:
  - `ai.requests.total` (`tenantId`, `model`, `outcome`)
  - `ai.request.latency` (`tenantId`, `model`)
  - `ai.requests.rejected` (`tenantId`, `reason`)

## Configuration

### Required/Typical

```properties
spring.ai.openai.api-key=${OPENAI_API_KEY}
spring.ai.openai.chat.options.model=gpt-4o-mini
management.endpoints.web.exposure.include=health,metrics
```

### Tenant Gateway

```properties
# auth mode: HEADER or JWT (default in properties class: HEADER)
ai.tenant.gateway.auth-type=HEADER

# rate limiter: IN_MEMORY, CAFFEINE, REDIS (default: CAFFEINE)
ai.tenant.gateway.rate-limit-strategy=CAFFEINE

# defaults for all tenants
ai.tenant.gateway.default-max-requests-per-minute=10
ai.tenant.gateway.default-max-tokens-per-day=100000

# per-tenant overrides
ai.tenant.gateway.tenants.acme.max-requests-per-minute=20
ai.tenant.gateway.tenants.acme.max-tokens-per-day=200000
ai.tenant.gateway.tenants.trial.max-requests-per-minute=3
ai.tenant.gateway.tenants.trial.max-tokens-per-day=10000

# header mode keys
ai.tenant.gateway.tenant-id-header=X-Tenant-Id
ai.tenant.gateway.team-id-header=X-Team-Id

# jwt mode claim names
ai.tenant.gateway.tenant-id-jwt-claim=tenant_id
ai.tenant.gateway.team-id-jwt-claim=team_id
```

### JWT Mode

When using `ai.tenant.gateway.auth-type=JWT`, configure resource server JWT verification:

```properties
spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:8080/oauth2/jwks
```

This project also includes Spring Authorization Server config for local flows (`/oauth2/token`, `/oauth2/jwks`).

## API Examples

### Chat

Request:

```json
{
  "messages": [
    { "role": "user", "content": "Hello" }
  ],
  "model": "gpt-4o-mini"
}
```

Successful response:

```json
{
  "content": "Hello",
  "tenantId": "acme",
  "latencyMs": 50
}
```

Rate-limited response:

```json
{
  "error": "rate_limit_exceeded: too many requests per minute",
  "retryAfterSeconds": 30
}
```

### Usage

`GET /api/v1/tenants/acme/usage`

```json
{
  "tenantId": "acme",
  "requestsThisMinute": 3,
  "maxRequestsPerMinute": 20,
  "tokensToday": 450,
  "maxTokensPerDay": 200000
}
```

## Strategy Notes

- `IN_MEMORY`: simple per-instance memory store.
- `CAFFEINE`: local cache-based limits (default).
- `REDIS`: shared limits across instances; requires Redis connection (`spring.data.redis.*`).

## Run

```bash
./mvnw spring-boot:run
```

## Test

```bash
./mvnw test
```

Notes:

- Redis integration tests use Testcontainers and are skipped when Docker is unavailable.
- Other unit/integration tests still run without Docker.

## License

Apache 2.0
