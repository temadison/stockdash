# Architecture

## Runtime Components

- `stockdash-backend`: Spring Boot REST API (Java 17+ compatible, Java 21 default toolchain)
- `stockdash-frontend`: placeholder module (current UI served from backend static assets)
- MySQL: persistent transactional store for accounts, trades, and daily close prices
- Alpha Vantage client: external market data source with retry/circuit-breaker/time-limiter

## Backend Layers

- API controllers: HTTP routing and input validation (`/api/portfolio/*`)
- Service ports (interfaces): controller/scheduler/seed depend on abstractions (`CsvImportService`, `PriceSyncService`, query services)
- Service implementations: business workflows (CSV import, price sync, summary/performance calculations)
- Repositories: Spring Data JPA persistence access
- Integration adapters: market data clients + request limiting + resilience policies
- Service support types: shared valuation primitives (for example, `PositionAccumulator`) to avoid duplicated state logic

## Data Model

- `accounts`
  - unique account name
- `trade_transactions`
  - natural-key uniqueness (`account_id`, `trade_date`, `symbol`, `type`, `quantity`, `price`, `fee`)
- `daily_close_prices`
  - unique (`symbol`, `price_date`)

Schema lifecycle is migration-driven with Flyway (`db/migration`), and runtime schema validation is enabled (`ddl-auto=validate`).

## Observability

- Actuator endpoints exposed for health/info/metrics/prometheus
- Liveness/readiness probes enabled
- Structured JSON logs via `logback-spring.xml`
- `X-Correlation-Id` propagation and request timing logs via filter

## Testing Strategy

- Unit/service/API tests under `test` task
- MySQL-backed Testcontainers integration tests under `integrationTest` task (`*IT`)
- `check` includes both test phases

## Local Data Sync Behavior

- Primary source: Alpha Vantage daily series API
- Local fallback (dev profile `seed-local`): synthetic close series from local trades when upstream is unavailable/rate-limited
- Fallback is intended for local development only
