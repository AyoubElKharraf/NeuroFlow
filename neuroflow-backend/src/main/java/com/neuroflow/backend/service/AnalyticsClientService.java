package com.neuroflow.backend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neuroflow.backend.repository.ClickHouseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Calls neuroflow-analytics for data-drift checks against ClickHouse production features.
 * Failures are logged and swallowed so the backend stays up if Python is down.
 */
@Service
public class AnalyticsClientService {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsClientService.class);
    private static final List<String> REQUIRED_FEATURES = List.of(
            "age", "income", "credit_score", "loan_amount"
    );

    private final RestClient analyticsRestClient;
    private final ClickHouseRepository clickHouseRepository;
    private final ObjectMapper objectMapper;
    private final AlertPublisherService alertPublisherService;
    private final boolean driftCheckEnabled;
    private final int sampleSize;
    private final AtomicReference<Map<String, Object>> lastDriftSummary = new AtomicReference<>(
            Map.of("datasetDrift", false, "driftShare", 0.0)
    );

    public AnalyticsClientService(
            RestClient analyticsRestClient,
            ClickHouseRepository clickHouseRepository,
            ObjectMapper objectMapper,
            AlertPublisherService alertPublisherService,
            @Value("${neuroflow.analytics.drift-check-enabled:true}") boolean driftCheckEnabled,
            @Value("${neuroflow.analytics.drift-sample-size:200}") int sampleSize) {
        this.analyticsRestClient = analyticsRestClient;
        this.clickHouseRepository = clickHouseRepository;
        this.objectMapper = objectMapper;
        this.alertPublisherService = alertPublisherService;
        this.driftCheckEnabled = driftCheckEnabled;
        this.sampleSize = sampleSize;
    }

    public Map<String, Object> lastDriftSummary() {
        return lastDriftSummary.get();
    }

    @Scheduled(fixedDelayString = "${neuroflow.analytics.drift-check-interval-ms:300000}")
    public void scheduledDriftCheck() {
        if (!driftCheckEnabled) {
            return;
        }
        try {
            runDriftCheck();
        } catch (Exception ex) {
            log.warn("Drift check skipped (analytics unavailable or error): {}", ex.getMessage());
        }
    }

    public Map<String, Object> runDriftCheck() {
        List<Map<String, Object>> currentData = loadCurrentFeatureRows();
        if (currentData.isEmpty()) {
            log.debug("No ML feature rows available for drift check yet");
            return Map.of(
                    "status", "SKIPPED",
                    "reason", "no_data",
                    "modelName", "loan-risk-model",
                    "datasetDrift", false,
                    "driftShare", 0.0,
                    "numberOfDriftedFeatures", 0,
                    "message", "No production features in ClickHouse yet — start the simulation",
                    "features", List.of(),
                    "sampleSize", 0
            );
        }

        Map<String, Object> requestBody = Map.of(
                "model_name", "loan-risk-model",
                "current_data", currentData
        );

        try {
            JsonNode response = analyticsRestClient.post()
                    .uri("/api/v1/drift/check")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(JsonNode.class);

            if (response == null) {
                log.warn("Empty response from analytics drift endpoint");
                return Map.of("status", "EMPTY_RESPONSE");
            }

            Map<String, Object> uiReport = toUiDriftReport(response, currentData.size());
            lastDriftSummary.set(Map.of(
                    "datasetDrift", uiReport.get("datasetDrift"),
                    "driftShare", uiReport.get("driftShare")
            ));

            boolean datasetDrift = Boolean.TRUE.equals(uiReport.get("datasetDrift"));
            double driftShare = ((Number) uiReport.getOrDefault("driftShare", 0.0)).doubleValue();
            int driftedFeatures = ((Number) uiReport.getOrDefault("numberOfDriftedFeatures", 0)).intValue();
            alertPublisherService.fromDrift(datasetDrift, driftShare, driftedFeatures, currentData.size());

            if (datasetDrift) {
                log.warn(
                        "DATA DRIFT DETECTED ! share={}, driftedFeatures={}, sampleSize={}",
                        uiReport.get("driftShare"),
                        uiReport.get("numberOfDriftedFeatures"),
                        currentData.size()
                );
            } else {
                log.info(
                        "Drift check OK (no dataset drift). share={}, sampleSize={}",
                        uiReport.get("driftShare"),
                        currentData.size()
                );
            }

            return uiReport;
        } catch (RestClientException ex) {
            log.warn(
                    "Analytics service unreachable at drift check time: {}",
                    ex.getMessage()
            );
            return Map.of(
                    "status", "ANALYTICS_DOWN",
                    "error", ex.getMessage() == null ? "unknown" : ex.getMessage()
            );
        }
    }

    private Map<String, Object> toUiDriftReport(JsonNode response, int sampleSize) {
        boolean datasetDrift = response.path("dataset_drift").asBoolean(false);
        double driftShare = response.path("drift_share").asDouble(0.0);
        int driftedFeatures = response.path("number_of_drifted_features").asInt(0);
        String modelName = response.path("model_name").asText("loan-risk-model");
        String message = response.path("message").asText(
                datasetDrift ? "DATA DRIFT DETECTED" : "No significant dataset drift detected"
        );

        List<Map<String, Object>> features = new ArrayList<>();
        JsonNode featureMetrics = response.path("feature_metrics");
        if (featureMetrics.isObject()) {
            featureMetrics.fields().forEachRemaining(entry -> {
                JsonNode metric = entry.getValue();
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("feature", entry.getKey());
                row.put("driftScore", metric.path("drift_score").asDouble(0.0));
                row.put("driftDetected", metric.path("drift_detected").asBoolean(false));
                row.put("statTestName", metric.path("stat_test_name").asText("unknown"));
                row.put("baselineMean", metric.path("baseline_mean").asDouble(0.0));
                row.put("productionMean", metric.path("production_mean").asDouble(0.0));
                features.add(row);
            });
        }

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("status", "OK");
        report.put("modelName", modelName);
        report.put("datasetDrift", datasetDrift);
        report.put("driftShare", driftShare);
        report.put("numberOfDriftedFeatures", driftedFeatures);
        report.put("message", message);
        report.put("features", features);
        report.put("sampleSize", sampleSize);
        report.put("source", "clickhouse");
        // Keep raw analytics fields for scripts / debugging
        report.put("dataset_drift", datasetDrift);
        report.put("drift_share", driftShare);
        report.put("number_of_drifted_features", driftedFeatures);
        report.put("model_name", modelName);
        report.put(
                "feature_metrics",
                objectMapper.convertValue(featureMetrics, new TypeReference<Map<String, Object>>() {
                })
        );
        return report;
    }

    private List<Map<String, Object>> loadCurrentFeatureRows() {
        List<String> rawRows = clickHouseRepository.fetchRecentMlFeatureJson(sampleSize);
        List<Map<String, Object>> parsed = new ArrayList<>();
        for (String json : rawRows) {
            try {
                Map<String, Object> features = objectMapper.readValue(json, new TypeReference<>() {
                });
                if (hasRequiredFeatures(features)) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (String key : REQUIRED_FEATURES) {
                        row.put(key, features.get(key));
                    }
                    parsed.add(row);
                }
            } catch (Exception ex) {
                log.debug("Skipping invalid features_json row: {}", ex.getMessage());
            }
        }
        return parsed;
    }

    private static boolean hasRequiredFeatures(Map<String, Object> features) {
        for (String key : REQUIRED_FEATURES) {
            if (!features.containsKey(key) || features.get(key) == null) {
                return false;
            }
        }
        return true;
    }
}
