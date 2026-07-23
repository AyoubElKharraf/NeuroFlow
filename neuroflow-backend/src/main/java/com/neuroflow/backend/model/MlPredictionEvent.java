package com.neuroflow.backend.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MlPredictionEvent(
        @JsonAlias({"modelId", "model_id", "model_name"}) String modelId,
        @JsonAlias({"modelVersion", "model_version"}) String modelVersion,
        @JsonAlias({"inferenceTimeMs", "inference_time_ms", "latency_ms"}) long inferenceTimeMs,
        String prediction,
        Map<String, Object> features,
        @JsonAlias({"ts", "timestamp", "event_time"}) Instant timestamp
) {
    public MlPredictionEvent {
        if (timestamp == null) {
            timestamp = Instant.now();
        }
        if (features == null) {
            features = Map.of();
        }
        if (modelId == null || modelId.isBlank()) {
            modelId = "unknown-model";
        }
        if (modelVersion == null || modelVersion.isBlank()) {
            modelVersion = "v0";
        }
        if (prediction == null) {
            prediction = "";
        }
    }
}
