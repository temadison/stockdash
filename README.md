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

## What Works Right Now

- `GET /api/health` -> basic health check
- `GET /api/portfolio/daily-summary?date=YYYY-MM-DD` -> transaction-based daily account summary (as-of date)
- `POST /api/portfolio/transactions/upload` -> upload buys/sells CSV (persists accounts + trades)
- `POST /api/portfolio/prices/sync` -> pull/store daily closes for a `stocks` array (stores only dates after each symbol's first `BUY` trade date)

Example sync request:

```bash
curl -X POST http://localhost:18090/api/portfolio/prices/sync \
  -H "Content-Type: application/json" \
  -d '{"stocks":["AAPL","MSFT","ASML"]}'
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
- Included seed file (`stockdash-backend/src/main/resources/seed/transactions.csv`) is synthetic demo data only.

For a demo deployment (for example `stockdash.temadison.com`), enable demo seed data:

```bash
export STOCKDASH_SEED_ENABLED=true
```

For a production-style empty startup, keep `STOCKDASH_SEED_ENABLED` unset (or `false`) and load real data through upload/API.

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

- `http://localhost:18090/api/health`
- `http://localhost:18090/api/portfolio/daily-summary?date=2026-02-16`

Example upload:

```bash
curl -X POST http://localhost:18090/api/portfolio/transactions/upload \
  -F "file=@transactions.csv"
```

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
export STOCKDASH_DB_USERNAME="stockdash-app"
export STOCKDASH_DB_PASSWORD="your_mysql_password"
```

Run backend with MySQL profile:

```bash
./gradlew :stockdash-backend:bootRunMysql
```

## Optional Startup Seeding

Seed import is off by default. To seed on app startup from classpath CSV:

1. Put your CSV at `stockdash-backend/src/main/resources/seed/transactions.csv`
2. Run one command:

```bash
./gradlew :stockdash-backend:bootRunSeed
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
   - `GET /api/health`
   - `GET /api/portfolio/daily-summary?date=2026-02-16`

## Next Build Steps

1. Add CSV upload endpoint for buys/sells (with fees) per account.
2. Persist transactions in PostgreSQL via Spring Data JPA.
3. Add daily valuation job using market price data.
4. Build frontend charts (stacked area by symbol, account totals over time, filters for day/week/month/year).
5. Add drill-down view by symbol and account.
