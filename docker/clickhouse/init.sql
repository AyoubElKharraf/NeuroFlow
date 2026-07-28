-- NeuroFlow ClickHouse bootstrap — neuroflow_analytics
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
ORDER BY (service_name, event_type, event_time)
TTL toDateTime(event_time) + INTERVAL 90 DAY;

CREATE TABLE IF NOT EXISTS neuroflow_analytics.app_logs
(
    log_id          String,
    service_name    LowCardinality(String),
    level           LowCardinality(String),
    message         String,
    trace_id        String,
    event_time      DateTime64(3, 'UTC'),
    ingested_at     DateTime64(3, 'UTC') DEFAULT now64(3)
)
ENGINE = MergeTree
PARTITION BY toYYYYMMDD(event_time)
ORDER BY (service_name, level, event_time)
TTL toDateTime(event_time) + INTERVAL 30 DAY;

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
ORDER BY (model_name, model_version, event_time)
TTL toDateTime(event_time) + INTERVAL 90 DAY;

CREATE TABLE IF NOT EXISTS neuroflow_analytics.metric_aggregates
(
    window_start    DateTime64(3, 'UTC'),
    window_end      DateTime64(3, 'UTC'),
    service_name    LowCardinality(String),
    metric_name     LowCardinality(String),
    metric_value    Float64,
    sample_count    UInt64
)
ENGINE = MergeTree
PARTITION BY toYYYYMMDD(window_start)
ORDER BY (service_name, metric_name, window_start);

CREATE TABLE IF NOT EXISTS neuroflow_analytics.drift_history
(
    check_id                    String,
    model_name                  LowCardinality(String),
    dataset_drift               UInt8,
    drift_share                 Float64,
    number_of_drifted_features  UInt16,
    sample_size                 UInt32,
    message                     String,
    features_json               String,
    trigger_source              LowCardinality(String),
    checked_at                  DateTime64(3, 'UTC'),
    ingested_at                 DateTime64(3, 'UTC') DEFAULT now64(3)
)
ENGINE = MergeTree
PARTITION BY toYYYYMMDD(checked_at)
ORDER BY (model_name, checked_at)
TTL toDateTime(checked_at) + INTERVAL 180 DAY;
