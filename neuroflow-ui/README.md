# neuroflow-ui

Observability dashboard — **Next.js 15 / TypeScript / Tailwind / Recharts**.

## Run

```bat
cd neuroflow-ui
npm install
npm run dev
```

Open: http://localhost:3000

Optional env (defaults shown):

```bat
set NEXT_PUBLIC_API_URL=http://localhost:8080
```

The dashboard talks to the **Spring backend only**. KPIs, performance charts and drift all resolve through ClickHouse via `/api/v1/metrics/*` and `/api/v1/analytics/drift/run`.

## Features

- Executive KPI overview from ClickHouse (events, p95/p99, error rate, drift badge)
- Live SSE log stream (`/api/v1/alerts/stream`) with mock fallback
- Drift monitor against production `ml_predictions.features_json` (Evidently via backend)
- Performance charts (req/min + inference latency, last 30 minutes)

## Verify

```bat
scripts\test-phase4.cmd
```
