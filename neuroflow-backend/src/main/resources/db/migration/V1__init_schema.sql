-- =============================================================================
-- Migration V1: Initial schema for NeuroFlow PostgreSQL database
-- Corresponds to JPA Entity: com.neuroflow.backend.domain.AppUser
-- =============================================================================

CREATE TABLE IF NOT EXISTS app_users (
    id            BIGSERIAL PRIMARY KEY,
    username      VARCHAR(64) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    roles         VARCHAR(128) NOT NULL,
    enabled       BOOLEAN NOT NULL DEFAULT TRUE
);

-- Index for optimized lookups on username
CREATE INDEX IF NOT EXISTS idx_app_users_username ON app_users (username);

