# Deploy Guide

## Profiles And Defaults

- Default profile: H2 + seed disabled
- MySQL runtime profile: `mysql`
- Local development seed profile: `seed-local`
- Demo seed profile: `seed`

## Required Environment Variables (MySQL)

- `PORT` (optional, default `18090`)
- `STOCKDASH_DB_URL`
- `STOCKDASH_DB_USERNAME`
- `STOCKDASH_DB_PASSWORD`

Recommended local username convention: `stockdash_app`.

## Health And Readiness

- Liveness: `GET /actuator/health/liveness`
- Readiness: `GET /actuator/health/readiness`
- General health: `GET /actuator/health`

Use readiness for deployment gating and container health checks.

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

## Rollback/Recovery

If a deployment is unhealthy:

1. Roll back to prior application image/tag.
2. Verify readiness endpoint returns `UP`.
3. Review logs for startup/migration failures.

If local seed data becomes inconsistent after CSV edits:

```bash
mysql -u root -p -e "DROP DATABASE IF EXISTS stockdash; CREATE DATABASE stockdash;"
./gradlew :stockdash-backend:bootRunMysql
```

## API Contract Access

- OpenAPI JSON: `GET /v3/api-docs`
- Swagger UI: `GET /swagger-ui/index.html`
