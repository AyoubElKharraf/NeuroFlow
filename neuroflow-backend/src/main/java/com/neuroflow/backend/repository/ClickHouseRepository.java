package com.neuroflow.backend.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neuroflow.backend.model.MlPredictionEvent;
import com.neuroflow.backend.model.TelemetryEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public class ClickHouseRepository {

    private static final Logger log = LoggerFactory.getLogger(ClickHouseRepository.class);

    private static final String INSERT_TELEMETRY = """
            INSERT INTO neuroflow_analytics.telemetry_events
            (event_id, service_name, event_type, latency_ms, http_status, payload, event_time)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String INSERT_ML = """
            INSERT INTO neuroflow_analytics.ml_predictions
            (prediction_id, model_name, model_version, latency_ms, confidence, features_json, prediction, event_time)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private final JdbcTemplate clickHouseJdbcTemplate;
    private final ObjectMapper objectMapper;

    public ClickHouseRepository(
            @Qualifier("clickHouseJdbcTemplate") JdbcTemplate clickHouseJdbcTemplate,
            ObjectMapper objectMapper) {
        this.clickHouseJdbcTemplate = clickHouseJdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public void insertTelemetry(TelemetryEvent event) {
        insertTelemetryBatch(List.of(event));
    }

    public void insertTelemetryBatch(List<TelemetryEvent> events) {
        if (events == null || events.isEmpty()) {
            return;
        }
        clickHouseJdbcTemplate.batchUpdate(INSERT_TELEMETRY, events, events.size(), (ps, event) -> {
            ps.setString(1, UUID.randomUUID().toString());
            ps.setString(2, event.service());
            ps.setString(3, "http");
            ps.setDouble(4, event.latencyMs());
            ps.setInt(5, event.status());
            ps.setString(6, toJson(event));
            ps.setTimestamp(7, toTimestamp(event.timestamp()));
        });
        log.debug("Inserted {} telemetry row(s) into ClickHouse", events.size());
    }

    public void insertMlPrediction(MlPredictionEvent event) {
        insertMlPredictionBatch(List.of(event));
    }

    public void insertMlPredictionBatch(List<MlPredictionEvent> events) {
        if (events == null || events.isEmpty()) {
            return;
        }
        clickHouseJdbcTemplate.batchUpdate(INSERT_ML, events, events.size(), (ps, event) -> {
            double confidence = extractConfidence(event);
            ps.setString(1, UUID.randomUUID().toString());
            ps.setString(2, event.modelId());
            ps.setString(3, event.modelVersion());
            ps.setDouble(4, event.inferenceTimeMs());
            ps.setDouble(5, confidence);
            ps.setString(6, toJson(event.features()));
            ps.setString(7, event.prediction());
            ps.setTimestamp(8, toTimestamp(event.timestamp()));
        });
        log.debug("Inserted {} ML prediction row(s) into ClickHouse", events.size());
    }

    public long countTelemetry() {
        Long count = clickHouseJdbcTemplate.queryForObject(
                "SELECT count() FROM neuroflow_analytics.telemetry_events",
                Long.class
        );
        return count == null ? 0L : count;
    }

    public long countMlPredictions() {
        Long count = clickHouseJdbcTemplate.queryForObject(
                "SELECT count() FROM neuroflow_analytics.ml_predictions",
                Long.class
        );
        return count == null ? 0L : count;
    }

    public List<String> fetchRecentMlFeatureJson(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 1000));
        return clickHouseJdbcTemplate.query(
                """
                        SELECT features_json
                        FROM neuroflow_analytics.ml_predictions
                        ORDER BY event_time DESC
                        LIMIT ?
                        """,
                (rs, rowNum) -> rs.getString("features_json"),
                safeLimit
        );
    }

    public void insertAppLog(com.neuroflow.backend.model.AlertEvent event) {
        clickHouseJdbcTemplate.update(
                """
                        INSERT INTO neuroflow_analytics.app_logs
                        (log_id, service_name, level, message, trace_id, event_time)
                        VALUES (?, ?, ?, ?, ?, ?)
                        """,
                event.id(),
                event.service(),
                event.level(),
                event.message(),
                "",
                toTimestamp(event.timestamp())
        );
    }

    public void insertMetricAggregates(Map<String, Object> kpis) {
        Instant now = Instant.now();
        Instant windowStart = now.minusSeconds(60);
        insertMetric(windowStart, now, "platform", "total_events", toDouble(kpis.get("totalEvents")), 1);
        insertMetric(windowStart, now, "platform", "latency_p95_ms", toDouble(kpis.get("latencyP95Ms")), 1);
        insertMetric(windowStart, now, "platform", "latency_p99_ms", toDouble(kpis.get("latencyP99Ms")), 1);
        insertMetric(windowStart, now, "platform", "error_rate_pct", toDouble(kpis.get("errorRatePct")), 1);
        insertMetric(windowStart, now, "platform", "drift_share", toDouble(kpis.get("driftShare")), 1);
    }

    private void insertMetric(
            Instant windowStart,
            Instant windowEnd,
            String service,
            String metricName,
            double value,
            long sampleCount) {
        clickHouseJdbcTemplate.update(
                """
                        INSERT INTO neuroflow_analytics.metric_aggregates
                        (window_start, window_end, service_name, metric_name, metric_value, sample_count)
                        VALUES (?, ?, ?, ?, ?, ?)
                        """,
                toTimestamp(windowStart),
                toTimestamp(windowEnd),
                service,
                metricName,
                value,
                sampleCount
        );
    }

    private static double toDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return 0.0;
    }

    /**
     * Latency percentiles over a rolling window (minutes). Returns zeros when empty.
     */
    public Map<String, Double> fetchLatencyPercentiles(int windowMinutes) {
        int minutes = Math.max(1, Math.min(windowMinutes, 1440));
        String sql = """
                SELECT
                    quantile(0.95)(latency_ms) AS p95,
                    quantile(0.99)(latency_ms) AS p99
                FROM neuroflow_analytics.telemetry_events
                WHERE event_time >= now64(3) - INTERVAL %d MINUTE
                """.formatted(minutes);
        return clickHouseJdbcTemplate.query(sql, rs -> {
            if (!rs.next()) {
                return Map.of("p95", 0.0, "p99", 0.0);
            }
            return Map.of(
                    "p95", sanitizeDouble(rs.getDouble("p95")),
                    "p99", sanitizeDouble(rs.getDouble("p99"))
            );
        });
    }

    /**
     * HTTP error rate (4xx+5xx) percentage over the last {@code windowMinutes}.
     */
    public double fetchErrorRatePct(int windowMinutes) {
        int minutes = Math.max(1, Math.min(windowMinutes, 1440));
        String sql = """
                SELECT
                    if(count() = 0, 0.0,
                       countIf(http_status >= 400) * 100.0 / count()) AS error_rate_pct
                FROM neuroflow_analytics.telemetry_events
                WHERE event_time >= now64(3) - INTERVAL %d MINUTE
                """.formatted(minutes);
        Double rate = clickHouseJdbcTemplate.query(sql, rs -> {
            if (!rs.next()) {
                return 0.0;
            }
            return sanitizeDouble(rs.getDouble("error_rate_pct"));
        });
        return rate == null ? 0.0 : rate;
    }

    /**
     * Percent change of event volume: recent window vs the immediately previous window.
     */
    public double fetchEventsTrendPct(int windowMinutes) {
        int minutes = Math.max(1, Math.min(windowMinutes, 720));
        String sql = """
                SELECT
                    countIf(event_time >= now64(3) - INTERVAL %d MINUTE) AS recent_count,
                    countIf(
                        event_time >= now64(3) - INTERVAL %d MINUTE
                        AND event_time < now64(3) - INTERVAL %d MINUTE
                    ) AS previous_count
                FROM neuroflow_analytics.telemetry_events
                WHERE event_time >= now64(3) - INTERVAL %d MINUTE
                """.formatted(minutes, minutes * 2, minutes, minutes * 2);
        Double trend = clickHouseJdbcTemplate.query(sql, rs -> {
            if (!rs.next()) {
                return 0.0;
            }
            long recent = rs.getLong("recent_count");
            long previous = rs.getLong("previous_count");
            if (previous == 0L) {
                return recent > 0L ? 100.0 : 0.0;
            }
            return ((recent - previous) * 100.0) / previous;
        });
        return trend == null ? 0.0 : trend;
    }

    /**
     * Per-minute request volume (telemetry) and average ML inference latency for charts.
     * Missing buckets are filled with zeros so the UI gets a continuous series.
     */
    public List<Map<String, Object>> fetchPerformanceSeries(int minutes) {
        int window = Math.max(5, Math.min(minutes, 180));

        Map<String, Long> requestsByBucket = new HashMap<>();
        String requestsSql = """
                SELECT
                    formatDateTime(toStartOfMinute(event_time), '%%Y-%%m-%%d %%H:%%M:00') AS bucket,
                    count() AS requests
                FROM neuroflow_analytics.telemetry_events
                WHERE event_time >= now64(3) - INTERVAL %d MINUTE
                GROUP BY bucket
                ORDER BY bucket
                """.formatted(window);
        clickHouseJdbcTemplate.query(requestsSql, rs -> {
            while (rs.next()) {
                requestsByBucket.put(rs.getString("bucket"), rs.getLong("requests"));
            }
            return null;
        });

        Map<String, Double> inferenceByBucket = new HashMap<>();
        String inferenceSql = """
                SELECT
                    formatDateTime(toStartOfMinute(event_time), '%%Y-%%m-%%d %%H:%%M:00') AS bucket,
                    avg(latency_ms) AS avg_inference_ms
                FROM neuroflow_analytics.ml_predictions
                WHERE event_time >= now64(3) - INTERVAL %d MINUTE
                GROUP BY bucket
                ORDER BY bucket
                """.formatted(window);
        clickHouseJdbcTemplate.query(inferenceSql, rs -> {
            while (rs.next()) {
                inferenceByBucket.put(
                        rs.getString("bucket"),
                        sanitizeDouble(rs.getDouble("avg_inference_ms"))
                );
            }
            return null;
        });


        List<Map<String, Object>> series = new ArrayList<>(window);
        LocalDateTime cursor = LocalDateTime.now(ZoneOffset.UTC)
                .withSecond(0)
                .withNano(0)
                .minusMinutes(window - 1L);
        DateTimeFormatter keyFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:00");
        DateTimeFormatter labelFmt = DateTimeFormatter.ofPattern("HH:mm");

        for (int i = 0; i < window; i++) {
            String key = cursor.format(keyFmt);
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("time", cursor.format(labelFmt));
            point.put("bucket", key);
            point.put("requestsPerMin", requestsByBucket.getOrDefault(key, 0L));
            point.put(
                    "inferenceLatencyMs",
                    Math.round(inferenceByBucket.getOrDefault(key, 0.0))
            );
            series.add(point);
            cursor = cursor.plusMinutes(1);
        }
        return series;
    }

    private static double sanitizeDouble(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0.0;
        }
        return value;
    }

    private static Timestamp toTimestamp(Instant instant) {
        return Timestamp.from(instant == null ? Instant.now() : instant);
    }

    private static double extractConfidence(MlPredictionEvent event) {
        Object raw = event.features().get("confidence");
        return switch (raw) {
            case Number n -> n.doubleValue();
            case String s -> {
                try {
                    yield Double.parseDouble(s);
                } catch (NumberFormatException ex) {
                    yield 0.0;
                }
            }
            case null, default -> 0.0;
        };
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            log.warn("Failed to serialize payload to JSON: {}", ex.getMessage());
            return "{}";
        }
    }
}
