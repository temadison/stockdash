# Deploy Guide

## Deployment Profiles

- Default profile: H2 + seed disabled
- MySQL runtime profile: `mysql`
- Local development seed profile: `seed-local`
- Demo seed profile: `seed`

## Environment Variables (MySQL Runtime)

Required:

- `STOCKDASH_DB_URL`: JDBC URL for target MySQL instance
- `STOCKDASH_DB_USERNAME`: application DB user
- `STOCKDASH_DB_PASSWORD`: application DB password

Optional:

- `PORT`: listener port (default `18090`)
- `STOCKDASH_SEED_ENABLED`: set `true` only for demo-style bootstraps
- `STOCKDASH_PRICING_ALPHA_VANTAGE_API_KEY`: market data API key
- `STOCKDASH_PRICING_SYNC_ENABLED`: enable scheduled sync job (`false` by default)
- `STOCKDASH_PRICING_SYNC_CRON`: cron for scheduled sync (default: `0 0 22 * * MON-FRI`)
- `STOCKDASH_PRICING_SYNC_ZONE`: timezone for cron evaluation (default: `UTC`)

Recommended local DB username convention: `stockdash_app`.

## Health And Readiness

- Liveness: `GET /actuator/health/liveness`
- Readiness: `GET /actuator/health/readiness`
- General health: `GET /actuator/health`

Use readiness for deployment gating and container health checks.

## Pre-Deploy Checklist

Run from repo root:

```bash
./gradlew :stockdash-backend:check
./gradlew :stockdash-backend:securityScan
```

Verify:

1. Unit tests are green.
2. Integration tests are green.
3. JaCoCo gate passes (`>= 75%` line coverage).
4. Dependency scan passes (fails on CVSS `>= 7.0`).

## Branch Protection

Use GitHub branch protection rules on `main` so deployable quality is enforced before merge:

- Require PR + approval.
- Require passing checks for `test (17)`, `test (21)`, and `security-scan`.
- Require branch up to date and conversations resolved.

Reference baseline: `docs/branch-protection.md`.

## Docker Compose (Local Prod-Like)

From repo root:

```bash
docker compose up --build
```

This starts:
- `mysql` (`mysql:8.4`) with persistent volume `stockdash_mysql_data`
- `backend` built from `stockdash-backend/Dockerfile`

Compose sets:
- `SPRING_PROFILES_ACTIVE=mysql`
- `STOCKDASH_SEED_ENABLED=false`

Post-start verification:

```bash
curl -fsS http://localhost:18090/actuator/health/readiness
curl -fsS http://localhost:18090/v3/api-docs > /dev/null
```

## Rollout Verification

After deploy, verify:

1. Readiness returns `UP`.
2. `/api/portfolio/symbols` returns HTTP `200`.
3. Logs do not show Flyway or datasource errors.

## Rollback Procedure

If a deployment is unhealthy:

1. Roll back to prior application image/tag (or last known good build).
2. Verify readiness endpoint returns `UP`.
3. Review logs for startup/migration failures.
4. Keep new rollout disabled until root cause is identified.

## Recovery Playbooks

### Database credential mismatch

Symptoms:
- startup datasource authentication failures
- readiness remains `DOWN`

Actions:

1. Validate runtime values for `STOCKDASH_DB_USERNAME` and `STOCKDASH_DB_PASSWORD`.
2. Validate DB grants for the configured user.
3. Restart backend after correcting credentials.

### Upstream market API rate-limited

Symptoms:
- `/api/portfolio/prices/sync` returns `rate_limited`/`circuit_open`
- no new rows in `daily_close_prices`

Actions:

1. Reduce sync batch size (1-2 symbols).
2. Retry after provider quota resets.
3. For local-only workflows, use `seed-local` profile fallback behavior.

If local seed data becomes inconsistent after CSV edits:

```bash
mysql -u root -p -e "DROP DATABASE IF EXISTS stockdash; CREATE DATABASE stockdash;"
./gradlew :stockdash-backend:bootRunMysql
```

## API Contract Access

- OpenAPI JSON: `GET /v3/api-docs`
- Swagger UI: `GET /swagger-ui/index.html`
