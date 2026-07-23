package com.neuroflow.backend.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TelemetryEvent(
        String service,
        @JsonAlias({"latency_ms", "latencyMs"}) long latencyMs,
        int status,
        @JsonAlias({"ts", "timestamp", "event_time"}) Instant timestamp,
        String endpoint
) {
    public TelemetryEvent {
        if (timestamp == null) {
            timestamp = Instant.now();
        }
        if (endpoint == null || endpoint.isBlank()) {
            endpoint = "/unknown";
        }
        if (service == null || service.isBlank()) {
            service = "unknown-service";
        }
    }
}
