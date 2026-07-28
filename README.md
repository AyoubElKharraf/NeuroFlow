<div align="center">

# NeuroFlow

### Observabilité temps réel & monitoring MLOps event-driven

[![CI](https://github.com/AyoubElKharraf/NeuroFlow/actions/workflows/ci.yml/badge.svg)](https://github.com/AyoubElKharraf/NeuroFlow/actions/workflows/ci.yml)
[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Kafka](https://img.shields.io/badge/Apache%20Kafka-Confluent-black.svg)](https://kafka.apache.org/)
[![ClickHouse](https://img.shields.io/badge/ClickHouse-24.8-yellow.svg)](https://clickhouse.com/)
[![FastAPI](https://img.shields.io/badge/FastAPI-0.110-009688.svg)](https://fastapi.tiangolo.com/)
[![Next.js](https://img.shields.io/badge/Next.js-15-black.svg)](https://nextjs.org/)
[![License](https://img.shields.io/badge/license-MIT-blue.svg)](#licence)

**Ingestion haute performance · Analytique instantanée · Détection de Data Drift**

<br />

![NeuroFlow dashboard — KPI, live SSE logs et performance](docs/dashboard.png)

</div>

---

Dans les architectures distribuées modernes, détecter les goulots d’étranglement et la dégradation silencieuse des modèles (*Data Drift*) reste un défi majeur. **NeuroFlow** est une plateforme d’observabilité event-driven qui capture, traite et analyse en temps réel des événements de télémétrie, tout en assurant le suivi MLOps des modèles en production.

---

## Sommaire

1. [Architecture globale](#architecture-globale)
2. [Piliers fonctionnels](#piliers-fonctionnels)
3. [Stack technique](#stack-technique)
4. [Structure du repository](#structure-du-repository)
5. [Prérequis](#prérequis)
6. [Installation & démarrage](#installation--démarrage)
7. [Dashboard](#dashboard)
8. [Inférences & analyse MLOps](#inférences--analyse-mlops)
9. [API REST & flux SSE](#api-rest--flux-sse)
10. [Tests & CI/CD](#tests--cicd)
11. [Roadmap](#roadmap)
12. [Licence](#licence)

---

## Architecture globale

Architecture **Event-Driven (EDA)** : faible latence, couplage faible, résilience.

```text
┌────────────────────────────────────────────────────────────────────────┐
│                         SOURCES DE DONNÉES                             │
│  [Microservice métier]              [Agent d'inférence ML]             │
└───────────────────┬────────────────────────────────┬───────────────────┘
                    │ Logs / télémétrie              │ Prédictions + features
                    ▼                                ▼
┌────────────────────────────────────────────────────────────────────────┐
│                   MESSAGE BROKER (Apache Kafka)                        │
│  Topics: telemetry-events · ml-predictions · alerts                    │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │ Spring Cloud Stream
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│            CORE BACKEND (Java 21 / Spring Boot 3.3)                    │
│  • Ingestion concurrente + alertes Kafka → SSE                         │
│  • Écriture analytique ClickHouse                                      │
│  • Auth JWT (users PostgreSQL) + simulation                            │
└──────────────┬────────────────────┬────────────────────┬───────────────┘
               │                    │                    │
               ▼                    ▼                    ▼
     ┌──────────────────┐  ┌──────────────────┐  ┌──────────────────────┐
     │    ClickHouse    │  │    PostgreSQL    │  │ FastAPI + Evidently  │
     │ KPI · logs · ML  │  │  users / meta    │  │ drift + predict      │
     └──────────────────┘  └──────────────────┘  └──────────┬───────────┘
                                                            │
                                                            ▼
                                               ┌──────────────────────┐
                                               │   Next.js Dashboard  │
                                               │  KPI · SSE · drift   │
                                               └──────────────────────┘
```

---

## Piliers fonctionnels

| Pilier | Description |
|--------|-------------|
| **Ingestion event-driven** | Producteurs Kafka → consommateurs Spring Cloud Stream → persistance ClickHouse |
| **Analytique temps réel** | Compteurs d’ingestion, métriques de latence/HTTP, agrégats stockés dans ClickHouse |
| **MLOps / Data Drift** | Comparaison production vs baseline via Evidently (FastAPI), appelée depuis le backend |
| **Observabilité live** | SSE branché sur Kafka `alerts` (erreurs HTTP, latence, drift) |
| **ML inference** | `POST /api/v1/predict` (sklearn LogisticRegression) appelé par la simulation |
| **Qualité & CI** | Testcontainers + pytest + Playwright smoke + GitHub Actions |

---

## Stack technique

| Couche | Technologies |
|--------|----------------|
| Backend | Java 21, Spring Boot 3.3, Spring Cloud Stream (Kafka), JPA, Redis |
| Analytique | ClickHouse 24.8, PostgreSQL 16 |
| MLOps | Python 3.11, FastAPI, Evidently, Pandas |
| Frontend | Next.js 15, TypeScript, Tailwind, Recharts |
| Infra locale | Docker Compose (Zookeeper, Kafka, Postgres, ClickHouse, Redis) |
| CI | GitHub Actions, Testcontainers, Maven, pytest, `next build` |

---

## Structure du repository

```text
NeuroFlow/
├── docs/
│   └── dashboard.png           # Screenshot UI
├── docker-compose.yml          # Infra locale
├── docker/                     # Scripts d'init Postgres / ClickHouse
├── .github/workflows/ci.yml    # Pipeline CI
├── scripts/                    # Smoke tests & helpers Windows
├── neuroflow-backend/          # Spring Boot — ingestion & API
├── neuroflow-analytics/        # FastAPI — drift Evidently
└── neuroflow-ui/               # Next.js — dashboard
```

| Module | Rôle |
|--------|------|
| `neuroflow-backend/` | Ingestion Kafka, écriture ClickHouse, simulation, SSE, client drift |
| `neuroflow-analytics/` | API drift (`/api/v1/drift/*`), baseline, rapport HTML |
| `neuroflow-ui/` | Dashboard dark (KPI, logs live, drift, performance) |

---

## Prérequis

- **Docker Desktop** (WSL2 recommandé sous Windows)
- **JDK 21+** (JDK 25 OK localement — éviter JDK 11)
- **Maven 3.9+**
- **Python 3.11+**
- **Node.js 20+** (22 recommandé pour la CI)

> Sous Windows, PostgreSQL Docker est exposé sur le port hôte **5433** (souvent `5432` est déjà pris par une instance locale).

---

## Installation & démarrage

### 1. Cloner & configurer

```bash
git clone https://github.com/AyoubElKharraf/NeuroFlow.git
cd NeuroFlow
cp .env.example .env   # Windows: Copy-Item .env.example .env
```

### 2. Démarrage full-stack (recommandé)

```bash
docker compose up -d --build
docker compose ps
```

| Service | Port hôte | Notes |
|---------|-----------|--------|
| Zookeeper | `2181` | Coordination Kafka |
| Kafka | `9092` | Topics créés par `kafka-init` |
| PostgreSQL | `5433` | DB `neuroflow_db` |
| ClickHouse | `8123` / `9000` | DB `neuroflow_analytics` |
| Redis | `6379` | Rate-limit alertes INFO |
| Analytics | `8001` | FastAPI + Evidently + predict |
| Backend | `8080` | Spring Boot (JWT users en Postgres) |
| UI | `3000` | Next.js dashboard |

| URL | Service |
|-----|---------|
| http://localhost:3000 | Dashboard |
| http://localhost:8080/api/v1/health | Backend |
| http://localhost:8001/health | Analytics |

Dans le dashboard (login `admin` / `admin123`) : **Start** lance la simulation Kafka → ClickHouse.

Infra seule (dev local des apps hors Docker) :

```bash
docker compose up -d zookeeper kafka kafka-init postgres clickhouse redis
```

### 3. Alternative : apps en local (3 terminaux)

Après infra healthy :

```bash
# Analytics — port 8001
scripts\run-analytics.cmd

# Backend — port 8080 (force JDK 17+ ; évite JAVA_HOME=JDK 11)
scripts\run-backend.cmd

# UI — port 3000
cd neuroflow-ui
npm install
npm run dev
```

### 4. Arrêt / reprise

```bat
REM Stack Docker complète
docker compose down

REM Fin de journée scripts locaux (volumes conservés)
scripts\stop-all.cmd

REM Reprise full-stack
docker compose up -d
```

Wipe total des données :

```bash
docker compose down -v
```

---

## Dashboard

Interface Next.js (thème dark) sur [http://localhost:3000](http://localhost:3000) — voir le screenshot en tête de README.

- **Login JWT** (modal) : `admin` / `admin123` — token stocké en `localStorage`, injecté sur l’API et le SSE
- **Simulation Kafka** : Start / Stop (ADMIN) + compteur d’événements émis
- KPI : événements, latence p95/p99, taux d’erreurs HTTP, drift — **agrégés depuis ClickHouse** (`GET /api/v1/metrics/kpis`)
- Live stream SSE (`/api/v1/alerts/stream?token=…`) — **événements Kafka `alerts`** (plus de ticks aléatoires)
- Graphiques de performance (req/min & latence inference) — **séries ClickHouse** (`GET /api/v1/metrics/performance`)
- Drift monitor : features production lues depuis `ml_predictions` puis analysées par Evidently (`POST /api/v1/analytics/drift/run`)
- **Drift history** : timeline ClickHouse (`drift_history`) + courbe share %

### Script démo entretien (~60 s)

1. Ouvrir http://localhost:3000 → login `admin` / `admin123`
2. Cliquer **Start** → badge `SIM RUNNING`, KPI/charts bougent
3. Observer le **Live Stream** : WARN/ERROR sur 4xx/5xx (topic Kafka `alerts`)
4. **Run Drift Check (ClickHouse)** → badge NO DRIFT / DRIFT + éventuelle alerte ERROR
5. **Stop** → simulation idle

```bash
# e2e smoke (UI up)
cd neuroflow-ui && npm run test:e2e

# e2e avec backend (simulation Start)
set PLAYWRIGHT_REQUIRE_BACKEND=1
npm run test:e2e
```

> Un **401** backend efface la session et réaffiche le formulaire de connexion. Le badge **MOCK MODE** apparaît si le backend est indisponible (fallback local).

---

## Inférences & analyse MLOps

Le module `neuroflow-analytics` compare un batch de features production à une **baseline** d’entraînement via Evidently.

Features suivies : `age`, `income`, `credit_score`, `loan_amount`.

```bash
# Santé
curl http://localhost:8001/health

# Drift check (corps JSON = current_data[])
curl -X POST http://localhost:8001/api/v1/drift/check ^
  -H "Content-Type: application/json" ^
  -d "{\"model_name\":\"loan-risk-model\",\"current_data\":[...]}"

# Rapport HTML
curl -o drift-report.html http://localhost:8001/api/v1/drift/report-html
```

Côté backend, un client Rest lit les `features_json` récents dans ClickHouse, puis appelle l’analytics (désactivable via `neuroflow.analytics.drift-check-enabled`). Le dashboard utilise le même chemin :

```bash
curl -X POST http://localhost:8080/api/v1/analytics/drift/run
```

---

## API REST & flux SSE

### Backend (`:8080`)

| Méthode | Endpoint | Auth | Description |
|---------|----------|------|-------------|
| `GET` | `/api/v1/health` | public | Santé du service |
| `POST` | `/api/v1/auth/login` | public | Login JWT (`admin`/`admin123`, `user`/`user123`) |
| `POST` | `/api/v1/simulation/start` | **ADMIN** | Démarre la génération d’événements Kafka |
| `POST` | `/api/v1/simulation/stop` | **ADMIN** | Arrête la simulation |
| `GET` | `/api/v1/simulation/status` | JWT | État de la simulation |
| `GET` | `/api/v1/metrics/ingestion` | JWT | Compteurs ClickHouse (telemetry / ML) |
| `GET` | `/api/v1/metrics/kpis` | JWT | KPI dashboard (p95/p99, error rate, trend) depuis ClickHouse |
| `GET` | `/api/v1/metrics/performance?minutes=30` | JWT | Série req/min + latence inference (agrégats ClickHouse) |
| `POST` | `/api/v1/analytics/drift/run` | JWT | Drift Evidently sur `features_json` ClickHouse (+ persist historique) |
| `GET` | `/api/v1/analytics/drift/history?limit=30` | JWT | Timeline des checks drift (ClickHouse `drift_history`) |
| `GET` | `/api/v1/alerts/stream` | JWT | **SSE** — `Authorization: Bearer` ou `?token=` |

Exemple login :

```bash
curl -X POST http://localhost:8080/api/v1/auth/login ^
  -H "Content-Type: application/json" ^
  -d "{\"username\":\"admin\",\"password\":\"admin123\"}"
```

### Analytics (`:8001`)

| Méthode | Endpoint | Description |
|---------|----------|-------------|
| `GET` | `/health` | Santé |
| `POST` | `/api/v1/drift/check` | Calcul de data drift |
| `GET` | `/api/v1/drift/report-html` | Rapport Evidently HTML |
| `GET` | `/api/v1/drift/baseline/sample` | Aperçu baseline |
| `GET` | `/api/v1/drift/samples/production-like` | Batch sans drift (tests) |
| `POST` | `/api/v1/predict` | Inférence `loan-risk-model` (sklearn) |
| `GET` | `/api/v1/predict/health` | Santé du modèle |

---

## Tests & CI/CD

### Smoke tests locaux (Windows)

```bat
scripts\kafka-smoke-test.cmd   REM Phase 1
scripts\test-phase2.cmd        REM Kafka → ClickHouse
scripts\test-phase3.cmd        REM Evidently (analytics up)
scripts\test-phase4.cmd        REM Build Next.js
scripts\test-phase5.cmd        REM Testcontainers + pytest + build UI
```

### Phase 5 — Testcontainers & GitHub Actions

```bat
scripts\test-phase5.cmd
```

Pipeline [`.github/workflows/ci.yml`](.github/workflows/ci.yml) sur chaque push/PR `main` :

| Job | Contenu |
|-----|---------|
| **Backend** | `mvn test` — Postgres, Kafka, Redis, ClickHouse (Testcontainers) |
| **Analytics** | `pytest` — health + drift normal/décalé |
| **UI** | `npm ci` + `next build` |

> Docker Engine **29+** : le projet force `api.version=1.44` pour Testcontainers (`docker-java.properties`). Sous Windows Docker Desktop, le script Phase 5 pointe `DOCKER_HOST` vers `dockerDesktopLinuxEngine` si besoin.

---

## Roadmap

- [x] **Phase 1** — Docker Compose + scaffolding multi-modules
- [x] **Phase 2** — Ingestion Kafka → ClickHouse + API simulation
- [x] **Phase 3** — Moteur de drift Evidently + client Spring
- [x] **Phase 4** — Dashboard Next.js (SSE, charts, drift)
- [x] **Phase 5** — Testcontainers + GitHub Actions CI
- [x] **Phase 6** — Brancher KPI / charts / drift UI sur agrégats ClickHouse (plus de mocks hardcodés)
- [x] **Phase 7** — Contrôles simulation dans le dashboard + Compose full-stack (apps + infra)
- [x] **Phase 8** — Alertes SSE ← Kafka (`alerts`) + drift ; users Postgres ; Redis rate-limit ; modèle sklearn + `/predict` ; Playwright smoke
- [x] **Phase 9** — Historique drift (ClickHouse `drift_history` + timeline UI)

---

## Licence

Distribué sous licence [MIT](LICENSE).
