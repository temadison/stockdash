# Stock Dashboard

Initial modular baseline for a stock dashboard that tracks multiple accounts over time.

## Recommended Stack (for this project)

- Backend: Java 21 + Spring Boot (REST API)
- Build: Gradle multi-module
- Data (now): in-memory sample data
- Data (next): PostgreSQL for trades + daily valuations
- Frontend (next): separate module (`stockdash-frontend`) to add a lightweight UI (React, or server-rendered if you prefer)

This keeps things easy to learn while staying modular and scalable.

## Current Modules

- `stockdash-backend`: runnable Spring Boot API
- `stockdash-frontend`: placeholder module for upcoming UI work

## What Works Right Now

- `GET /api/health` -> basic health check
- `GET /api/portfolio/daily-summary?date=YYYY-MM-DD` -> transaction-based daily account summary (as-of date)
- `POST /api/portfolio/transactions/upload` -> upload buys/sells CSV (persists accounts + trades)

Daily summary valuation currently uses:

- Net quantity per symbol as of date (`BUY` minus `SELL`)
- Last traded price for each symbol as of date
- Cumulative fees subtracted from each symbol value

### CSV Format

Required headers:

- `trade_date` (ISO date, example: `2026-02-16`)
- `account` (example: `IRA`)
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

- `http://localhost:8080/api/health`
- `http://localhost:8080/api/portfolio/daily-summary?date=2026-02-16`

Example upload:

```bash
curl -X POST http://localhost:8080/api/portfolio/transactions/upload \
  -F "file=@transactions.csv"
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
