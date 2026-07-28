-- NeuroFlow PostgreSQL bootstrap (neuroflow_db)
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE IF NOT EXISTS schema_meta (
    key         TEXT PRIMARY KEY,
    value       TEXT NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS app_users (
    id            BIGSERIAL PRIMARY KEY,
    username      VARCHAR(64) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    roles         VARCHAR(128) NOT NULL,
    enabled       BOOLEAN NOT NULL DEFAULT TRUE
);

INSERT INTO schema_meta (key, value)
VALUES ('phase', '7-fullstack-alerts')
ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value, updated_at = NOW();
