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
│  Topics: telemetry-events · ml-predictions · app-logs · alerts · …     │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │ Spring Cloud Stream
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│            CORE BACKEND (Java 21 / Spring Boot 3.3)                    │
│  • Ingestion concurrente                                               │
│  • Écriture analytique ClickHouse                                      │
│  • Simulation d’événements + alerting SSE                              │
└──────────────┬────────────────────┬────────────────────┬───────────────┘
               │                    │                    │
               ▼                    ▼                    ▼
     ┌──────────────────┐  ┌──────────────────┐  ┌──────────────────┐
     │    ClickHouse    │  │    PostgreSQL    │  │ FastAPI + Evidently│
     │   (analytique)   │  │  (métadonnées)   │  │   (data drift)    │
     └──────────────────┘  └──────────────────┘  └─────────┬────────┘
                                                           │
                                                           ▼
                                              ┌──────────────────────┐
                                              │   Next.js Dashboard  │
                                              │  KPI · logs · drift  │
                                              └──────────────────────┘
```

---

## Piliers fonctionnels

| Pilier | Description |
|--------|-------------|
| **Ingestion event-driven** | Producteurs Kafka → consommateurs Spring Cloud Stream → persistance ClickHouse |
| **Analytique temps réel** | Compteurs d’ingestion, métriques de latence/HTTP, agrégats stockés dans ClickHouse |
| **MLOps / Data Drift** | Comparaison production vs baseline via Evidently (FastAPI), appelée depuis le backend |
| **Observabilité live** | Dashboard Next.js + flux SSE d’alertes (`/api/v1/alerts/stream`) |
| **Qualité & CI** | Testcontainers (Postgres, Kafka, Redis, ClickHouse) + GitHub Actions |

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

### 2. Infrastructure

```bash
docker compose up -d
docker compose ps
```

| Service | Port hôte | Notes |
|---------|-----------|--------|
| Zookeeper | `2181` | Coordination Kafka |
| Kafka | `9092` | Topics créés par `kafka-init` |
| PostgreSQL | `5433` | DB `neuroflow_db` |
| ClickHouse | `8123` / `9000` | DB `neuroflow_analytics` |
| Redis | `6379` | Cache / rate-limit alertes |

Smoke Kafka :

```bash
# Windows
scripts\kafka-smoke-test.cmd
```

### 3. Services applicatifs

Dans **3 terminaux** (après infra healthy) :

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

| URL | Service |
|-----|---------|
| http://localhost:8080/api/v1/health | Backend |
| http://localhost:8001/health | Analytics |
| http://localhost:3000 | Dashboard |

### 4. Arrêt / reprise

```bat
REM Fin de journée (volumes conservés)
scripts\stop-all.cmd

REM Reprise
docker compose up -d
scripts\run-analytics.cmd
scripts\run-backend.cmd
cd neuroflow-ui && npm run dev
```

Wipe total des données :

```bash
docker compose down -v
```

---

## Dashboard

Interface Next.js (thème dark) sur [http://localhost:3000](http://localhost:3000) — voir le screenshot en tête de README.

- **Login JWT** (modal) : `admin` / `admin123` — token stocké en `localStorage`, injecté sur l’API et le SSE
- KPI : événements ingérés, latence p95/p99, taux d’erreurs HTTP, statut de data drift
- Live stream SSE (`/api/v1/alerts/stream?token=…`) avec filtres INFO / WARN / ERROR
- Graphiques de performance (volume & latence ML)

> Un **401** backend efface la session et réaffiche le formulaire de connexion. Le badge **MOCK MODE** apparaît si analytics/API sont indisponibles.

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

Côté backend, un client Rest appelle périodiquement l’analytics (désactivable via `neuroflow.analytics.drift-check-enabled`). Déclenchement manuel :

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
| `POST` | `/api/v1/analytics/drift/run` | JWT | Lance un check drift via analytics |
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

---

## Licence

Distribué sous licence [MIT](LICENSE).
