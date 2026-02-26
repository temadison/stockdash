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

## Structure

- `src/app`: app shell, router, global styles
- `src/features`: feature modules (`summary`, `history`, `performance`, etc.)
- `src/shared/api`: typed HTTP client + API functions
- `src/shared/types`: DTO types aligned to backend API responses
- `src/shared/ui`: reusable UI primitives
- `src/shared/utils`: formatting/time helpers
