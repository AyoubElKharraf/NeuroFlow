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
set NEXT_PUBLIC_ANALYTICS_URL=http://localhost:8001
```

## Features

- Executive KPI overview (events, latency, error rate, drift badge)
- Live SSE log stream (`/api/v1/alerts/stream`) with mock fallback
- Drift monitor + manual check against analytics service
- Performance charts (30-minute window)

## Verify

```bat
scripts\test-phase4.cmd
```
