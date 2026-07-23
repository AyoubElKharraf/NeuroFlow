# NeuroFlow

Event-driven observability & MLOps platform: Kafka ingestion, Spring Boot core engine, ClickHouse analytics, FastAPI drift detection, Next.js live dashboard.

## Modules

| Path | Stack | Role |
|------|-------|------|
| `neuroflow-backend/` | Java 21 · Spring Boot 3.3 · Cloud Stream | Ingestion, aggregation, alerting |
| `neuroflow-analytics/` | Python 3.11 · FastAPI · Evidently | Data drift & ML metrics |
| `neuroflow-ui/` | Next.js 15 · TypeScript · Tailwind | Observability dashboard |

## Phase 1 — Infrastructure (one command)

### Prerequisites

- Docker Desktop (WSL2 on Windows)

### Start all infra services

```powershell
Copy-Item .env.example .env
docker compose up -d
docker compose ps
```

| Service | Host | Notes |
|---------|------|-------|
| Zookeeper | `localhost:2181` | Kafka coordination |
| Kafka | `localhost:9092` | Topics auto-created by `kafka-init` |
| PostgreSQL | `localhost:5433` | DB `neuroflow_db` (host `5433` — local PG often occupies `5432`) |
| ClickHouse | `8123` / `9000` | DB `neuroflow_analytics` |
| Redis | `localhost:6379` | Cache & alert rate-limiting |

### Kafka smoke test

```powershell
.\scripts\kafka-smoke-test.ps1
```

### Stop

```powershell
docker compose down
# wipe volumes:
docker compose down -v
```

## Run application modules (after infra is healthy)

```powershell
# Backend
cd neuroflow-backend
mvn spring-boot:run

# Analytics (new terminal)
cd neuroflow-analytics
python -m venv .venv; .\.venv\Scripts\Activate.ps1
pip install -r requirements.txt
uvicorn app.main:app --reload --port 8000

# UI (new terminal)
cd neuroflow-ui
npm install
npm run dev
```

## Roadmap

- [x] **Phase 1** — Docker Compose + multi-module scaffolding
- [x] **Phase 2** — ClickHouse writer, Kafka consumers, simulation API
- [x] **Phase 3** — Evidently drift engine + Spring analytics client
- [ ] **Phase 4** — Full dashboard (live logs, drift charts, health grid)
- [ ] **Phase 5** — Testcontainers + GitHub Actions CI
