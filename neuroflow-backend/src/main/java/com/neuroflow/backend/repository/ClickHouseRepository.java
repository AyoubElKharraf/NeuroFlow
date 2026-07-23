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
import java.util.List;
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
