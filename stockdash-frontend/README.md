# stockdash-frontend

Modular React + TypeScript frontend for the StockDash backend APIs.

## Run locally

1. Install dependencies:

```bash
cd stockdash-frontend
npm install
```

2. Start the dev server:

```bash
npm run dev
```

By default it runs on `http://localhost:5173` and proxies `/api`, `/actuator`, `/v3`, `/swagger-ui` to backend at `http://localhost:18090`.

## Build

```bash
npm run build
npm run preview
```

## Summary Page

The summary page loads account snapshots from `/api/portfolio/daily-summary` and whole-portfolio performance through `/api/portfolio/performance`. It displays total portfolio net gain/loss, return, and CAGR above the account list for the selected as-of date, and each account position includes cost basis, gain/loss, return, and CAGR.

## Structure

- `src/app`: app shell, router, global styles
- `src/features`: feature modules (`summary`, `history`, `performance`, etc.)
- `src/shared/api`: typed HTTP client + API functions
- `src/shared/types`: DTO types aligned to backend API responses
- `src/shared/ui`: reusable UI primitives
- `src/shared/utils`: formatting/time helpers
