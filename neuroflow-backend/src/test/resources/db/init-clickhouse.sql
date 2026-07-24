CREATE DATABASE IF NOT EXISTS neuroflow_analytics;

CREATE TABLE IF NOT EXISTS neuroflow_analytics.telemetry_events
(
    event_id        String,
    service_name    LowCardinality(String),
    event_type      LowCardinality(String),
    latency_ms      Float64,
    http_status     UInt16,
    payload         String,
    event_time      DateTime64(3, 'UTC'),
    ingested_at     DateTime64(3, 'UTC') DEFAULT now64(3)
)
ENGINE = MergeTree
PARTITION BY toYYYYMMDD(event_time)
ORDER BY (service_name, event_type, event_time);

CREATE TABLE IF NOT EXISTS neuroflow_analytics.ml_predictions
(
    prediction_id   String,
    model_name      LowCardinality(String),
    model_version   LowCardinality(String),
    latency_ms      Float64,
    confidence      Float64,
    features_json   String,
    prediction      String,
    event_time      DateTime64(3, 'UTC'),
    ingested_at     DateTime64(3, 'UTC') DEFAULT now64(3)
)
ENGINE = MergeTree
PARTITION BY toYYYYMMDD(event_time)
ORDER BY (model_name, model_version, event_time);
