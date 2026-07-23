# neuroflow-backend

Core Engine — **Java 21 / Spring Boot 3.3** + Spring Cloud Stream (Kafka) + ClickHouse.

## Phase 2 features

- Kafka consumers: `telemetry-events`, `ml-predictions`
- Batch inserts into ClickHouse (`telemetry_events`, `ml_predictions`)
- Simulation API via StreamBridge

## Run

```powershell
docker compose up -d
cd neuroflow-backend
mvn spring-boot:run
```

### Useful endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/v1/health` | Liveness |
| POST | `/api/v1/simulation/start` | Start fake traffic (~5 msg/s) |
| POST | `/api/v1/simulation/stop` | Stop simulation |
| GET | `/api/v1/simulation/status` | Simulation status |
| GET | `/api/v1/metrics/ingestion` | ClickHouse row counts |

### Verify

```powershell
scripts\test-phase2.cmd
```
