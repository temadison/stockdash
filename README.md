# Stock Dashboard

Initial modular baseline for a stock dashboard that tracks multiple accounts over time.

## Recommended Stack (for this project)

- Backend: Java 21 + Spring Boot (REST API)
- Build: Gradle multi-module
- Data (now): in-memory sample data
- Data: H2 by default, MySQL profile available
- Frontend (next): separate module (`stockdash-frontend`) to add a lightweight UI (React, or server-rendered if you prefer)

This keeps things easy to learn while staying modular and scalable.

## Current Modules

- `stockdash-backend`: runnable Spring Boot API
- `stockdash-frontend`: placeholder module for upcoming UI work

## Documentation

- Architecture: [docs/architecture.md](docs/architecture.md)
- Deploy/runbook: [docs/deploy.md](docs/deploy.md)
- Branch protection: [docs/branch-protection.md](docs/branch-protection.md)

## 5-Minute Quick Start (Enforced Path)

Goal: fresh clone, one command, healthy app.

From repo root:

```bash
docker compose --env-file .env.example up --build -d
```

Verify health:

```bash
curl -fsS http://localhost:18090/actuator/health/readiness
```

Expected response includes `"status":"UP"`.

Quick-start behavior:
- MySQL starts in Docker.
- Flyway migrations run automatically on backend startup.
- Demo seed import is enabled in `.env.example` (`STOCKDASH_SEED_ENABLED=true`).

Stop:

```bash
docker compose down
```

Reset all data:

```bash
docker compose down -v
```

Required env vars for compose quick start live in `.env.example`:
- `PORT`
- `SPRING_PROFILES_ACTIVE`
- `STOCKDASH_SEED_ENABLED`
- `STOCKDASH_DB_PORT`
- `STOCKDASH_DB_NAME`
- `STOCKDASH_DB_USERNAME`
- `STOCKDASH_DB_PASSWORD`
- `STOCKDASH_DB_ROOT_PASSWORD`

## Observability Pack

Already built into backend:
- Structured JSON logs with propagated `X-Correlation-Id` (`CorrelationIdFilter` + `logback-spring.xml`)
- Actuator health probes:
  - `/actuator/health`
  - `/actuator/health/liveness`
  - `/actuator/health/readiness`
- Micrometer Prometheus metrics endpoint:
  - `/actuator/prometheus`

Ops artifacts:
- Prometheus config: `ops/prometheus/prometheus.yml`
- Grafana provisioning + dashboard JSON:
  - `ops/grafana/provisioning/datasources/prometheus.yml`
  - `ops/grafana/provisioning/dashboards/dashboards.yml`
  - `ops/grafana/stockdash-overview-dashboard.json`

Run metrics stack with compose:

```bash
docker compose --env-file .env.example up --build -d
```

View:
- Prometheus UI: `http://localhost:9090`
- Grafana UI: `http://localhost:3000`
  - default creds from `.env.example`: `admin` / `admin`
  - dashboard: `Stockdash / Stockdash Backend Overview`

## Portfolio Readiness Signals

- Migration-managed schema (Flyway) with startup validation.
- Health probes and metrics via Actuator (`liveness`, `readiness`, Prometheus).
- Structured logs with correlation IDs for request tracing.
- Unit + MySQL Testcontainers integration coverage in CI.
- Coverage gate enforced in Gradle (`>= 75%` line coverage).
- Security scan + CycloneDX SBOM generated in CI.

## What Works Right Now

- `GET /actuator/health` -> service health check
- `GET /api/portfolio/daily-summary?date=YYYY-MM-DD` -> transaction-based daily account summary (as-of date)
- `POST /api/portfolio/transactions/upload` -> upload buys/sells CSV (persists accounts + trades)
- `POST /api/portfolio/prices/sync` -> pull/store daily closes for a `stocks` array (stores only dates after each symbol's first `BUY` trade date)
- `GET /v3/api-docs` + `GET /swagger-ui/index.html` -> OpenAPI contract and interactive docs

Example sync request:

```bash
curl -X POST http://localhost:18090/api/portfolio/prices/sync \
  -H "Content-Type: application/json" \
  -d '{"stocks":["AAPL","MSFT","ASML"]}'
```

Validation error example:

```bash
curl -i -X POST http://localhost:18090/api/portfolio/prices/sync \
  -H "Content-Type: application/json" \
  -d '{}'
```

Daily summary valuation currently uses:

- Net quantity per symbol as of date (`BUY` minus `SELL`)
- Market close price (Alpha Vantage) on or before the as-of date
- Cumulative fees subtracted from each symbol value

Configure pricing API key:

```bash
export STOCKDASH_PRICING_ALPHA_VANTAGE_API_KEY=your_api_key_here
```

Security note:

- Never commit real API keys or DB passwords to the repo.
- Never paste real secrets into chat, screenshots, or shared docs.
- If a secret is exposed, rotate it immediately and update local env vars/run configs.

If no API key is configured, the service falls back to each symbol's last trade price in your transaction history.

## Cloud Deployment Defaults

- App port supports cloud routing via `PORT`:
  - `server.port=${PORT:18090}`
- Seed import remains off by default in base config.
- Included seed file (`stockdash-backend/src/main/resources/seed/demo-transactions.csv`) is synthetic demo data only.

For a demo deployment (for example `stockdash.temadison.com`), enable demo seed data:

```bash
export STOCKDASH_SEED_ENABLED=true
```

For a production-style empty startup, keep `STOCKDASH_SEED_ENABLED` unset (or `false`) and load real data through upload/API.

### Static Portfolio Deployment (Vercel)

For a zero-backend portfolio demo, deploy the static site folder directly:

1. In Vercel, import this repo.
2. Set **Root Directory** to `stockdash-backend/src/main/resources/static`.
3. Leave build/install commands empty.
4. Deploy.

The frontend will automatically use local demo data when `/api/*` endpoints are unavailable.
Upload/sync actions remain visible but run in demo mode (no backend writes).

### CSV Format

Required headers:

- `trade_date` (ISO date, example: `2026-02-16`)
- `account` (example: `DEMO_GROWTH`)
- `symbol` (example: `AAPL`)
- `type` (`BUY` or `SELL`)
- `quantity` (must be `> 0`)
- `price` (must be `>= 0`)
- `fee` (must be `>= 0`)

## Run From Terminal

```bash
./gradlew :stockdash-backend:bootRun
```

Then open:

- `http://localhost:18090/actuator/health`
- `http://localhost:18090/api/portfolio/daily-summary?date=2026-02-16`

Example upload:

```bash
curl -X POST http://localhost:18090/api/portfolio/transactions/upload \
  -F "file=@transactions.csv"
```

### Verification And Packaging

Run the full backend verification pipeline (unit + integration tests):

```bash
./gradlew :stockdash-backend:check
```

Generate JaCoCo coverage report and enforce the line-coverage gate:

```bash
./gradlew :stockdash-backend:jacocoTestCoverageVerification
```

Current gate: bundle line coverage must be at least `75%`.

Coverage reports are written to:
- `stockdash-backend/build/reports/jacoco/test/html/index.html`

Performance smoke tests for key read endpoints (`/api/portfolio/daily-summary`, `/api/portfolio/performance`) run as part of `:stockdash-backend:test`.

### Security Scanning And SBOM

Run dependency vulnerability scan (OWASP Dependency-Check) and generate CycloneDX SBOM:

```bash
./gradlew :stockdash-backend:securityScan
```

By policy, vulnerabilities with CVSS `>= 7.0` fail the scan.

Generated artifacts:
- `stockdash-backend/build/reports/dependency-check-report.html`
- `stockdash-backend/build/reports/dependency-check-report.json`
- `stockdash-backend/build/reports/bom.json`
- `stockdash-backend/build/reports/bom.xml`

Build backend artifact:

```bash
./gradlew :stockdash-backend:bootJar
```

The backend JAR manifest includes:
- `Implementation-Version` (project version)
- `Implementation-Commit` (short git SHA, when available)

### Dev Auto-Restart (No More Ctrl-C Loops)

DevTools is enabled for the backend module. Keep one `bootRun` process running:

```bash
./gradlew :stockdash-backend:bootRun
```

Then edit and save backend files; Spring Boot restarts automatically.
For static frontend files (`static/*.html`, `static/*.js`, `static/*.css`), browser refresh is usually enough.

### Run With MySQL

Start MySQL (Homebrew on macOS):

```bash
brew services start mysql
```

Set credentials:

```bash
export STOCKDASH_DB_URL="jdbc:mysql://localhost:3306/stockdash?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
export STOCKDASH_DB_USERNAME="stockdash_app"
export STOCKDASH_DB_PASSWORD="your_mysql_password"
```

Run backend with MySQL profile:

```bash
./gradlew :stockdash-backend:bootRunMysql
```

Note: `bootRun` and `bootRunMysql` now default to `mysql` only (no local fallback profile).
If you explicitly run with `seed-local`, fallback price generation is enabled (`stockdash.pricing.local-fallback-enabled=true`), so `/api/portfolio/prices/sync` can populate local `daily_close_prices` when Alpha Vantage is rate-limited.

### Run With Docker Compose (Backend + MySQL)

Start everything with containers:

```bash
docker compose up --build
```

Then verify readiness:

```bash
curl http://localhost:18090/actuator/health/readiness
```

Notes:
- Compose seeds are off by default (`STOCKDASH_SEED_ENABLED=false`).
- The enforced quick-start command uses `.env.example`, which sets `STOCKDASH_SEED_ENABLED=true` for demo data.
- MySQL data persists in the named volume `stockdash_mysql_data`.

## Optional Startup Seeding

Seed import is off by default. To seed on app startup from classpath CSV:

1. Demo seed (tracked):

```bash
./gradlew :stockdash-backend:bootRunSeed
```

2. Personal local seed (not tracked):
   - copy `stockdash-backend/src/main/resources/seed/local-transactions.template.csv`
     to `stockdash-backend/src/main/resources/seed/local-transactions.csv`
   - put your real transactions in `local-transactions.csv`
   - run:

```bash
./gradlew :stockdash-backend:bootRunSeedLocal
```

If you edit `local-transactions.csv` and need corrected rows to replace existing data, reset the DB first (seed import is append/idempotent and does not update existing rows):

```bash
mysql -u root -p -e "DROP DATABASE IF EXISTS stockdash; CREATE DATABASE stockdash;"
./gradlew :stockdash-backend:bootRunMysql
```

## IntelliJ Step-By-Step

1. Open IntelliJ IDEA.
2. Click **Open** and select this folder: `stock-dashboard`.
3. When prompted, trust the project and import as a **Gradle** project.
4. In the Gradle tool window, refresh the project.
5. Set project SDK to Java 21:
   - `File` -> `Project Structure` -> `Project SDK`.
6. Run the backend:
   - Gradle -> `stockdash-backend` -> `Tasks` -> `application` -> `bootRun`.
7. Verify endpoints in browser/Postman:
   - `GET /actuator/health`
   - `GET /api/portfolio/daily-summary?date=2026-02-16`

## Next Build Steps

1. Add CSV upload endpoint for buys/sells (with fees) per account.
2. Persist transactions in PostgreSQL via Spring Data JPA.
3. Add daily valuation job using market price data.
4. Build frontend charts (stacked area by symbol, account totals over time, filters for day/week/month/year).
5. Add drill-down view by symbol and account.
