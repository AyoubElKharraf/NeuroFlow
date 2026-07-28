package com.neuroflow.backend.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AlertEvent(
        String id,
        Instant timestamp,
        String level,
        String service,
        String message
) {
    public AlertEvent {
        if (id == null || id.isBlank()) {
            id = UUID.randomUUID().toString();
        }
        if (timestamp == null) {
            timestamp = Instant.now();
        }
        if (level == null || level.isBlank()) {
            level = "INFO";
        }
        if (service == null || service.isBlank()) {
            service = "neuroflow";
        }
        if (message == null) {
            message = "";
        }
    }

    public static AlertEvent of(String level, String service, String message) {
        return new AlertEvent(UUID.randomUUID().toString(), Instant.now(), level, service, message);
    }
}
