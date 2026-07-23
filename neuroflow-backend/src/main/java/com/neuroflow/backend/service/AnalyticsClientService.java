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

/**
 * Calls neuroflow-analytics for data-drift checks.
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
    private final boolean driftCheckEnabled;
    private final int sampleSize;

    public AnalyticsClientService(
            RestClient analyticsRestClient,
            ClickHouseRepository clickHouseRepository,
            ObjectMapper objectMapper,
            @Value("${neuroflow.analytics.drift-check-enabled:true}") boolean driftCheckEnabled,
            @Value("${neuroflow.analytics.drift-sample-size:200}") int sampleSize) {
        this.analyticsRestClient = analyticsRestClient;
        this.clickHouseRepository = clickHouseRepository;
        this.objectMapper = objectMapper;
        this.driftCheckEnabled = driftCheckEnabled;
        this.sampleSize = sampleSize;
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
            return Map.of("status", "SKIPPED", "reason", "no_data");
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

            boolean datasetDrift = response.path("dataset_drift").asBoolean(false);
            double driftShare = response.path("drift_share").asDouble(0.0);
            int driftedFeatures = response.path("number_of_drifted_features").asInt(0);

            if (datasetDrift) {
                log.warn(
                        "DATA DRIFT DETECTED ! share={}, driftedFeatures={}, sampleSize={}",
                        driftShare,
                        driftedFeatures,
                        currentData.size()
                );
            } else {
                log.info(
                        "Drift check OK (no dataset drift). share={}, sampleSize={}",
                        driftShare,
                        currentData.size()
                );
            }

            return objectMapper.convertValue(response, new TypeReference<>() {
            });
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
