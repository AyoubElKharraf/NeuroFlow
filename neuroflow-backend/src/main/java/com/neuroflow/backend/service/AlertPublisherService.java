package com.neuroflow.backend.service;

import com.neuroflow.backend.model.AlertEvent;
import com.neuroflow.backend.model.MlPredictionEvent;
import com.neuroflow.backend.model.TelemetryEvent;
import com.neuroflow.backend.repository.ClickHouseRepository;
import io.micrometer.core.instrument.Counter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Publishes operational alerts to Kafka {@code alerts}. Optionally rate-limits noisy
 * INFO lines via Redis, and mirrors rows into ClickHouse {@code app_logs}.
 */
@Service
public class AlertPublisherService {

    private static final Logger log = LoggerFactory.getLogger(AlertPublisherService.class);
    private static final String BINDING = "alerts-out";
    private static final String RATE_KEY_PREFIX = "neuroflow:alert:rate:";

    private final StreamBridge streamBridge;
    private final ClickHouseRepository clickHouseRepository;
    private final StringRedisTemplate redisTemplate;
    private final Counter alertsPublishedCounter;
    private final AtomicLong infoCounter = new AtomicLong();

    public AlertPublisherService(
            StreamBridge streamBridge,
            ClickHouseRepository clickHouseRepository,
            StringRedisTemplate redisTemplate,
            @Qualifier("alertsPublishedCounter") Counter alertsPublishedCounter) {
        this.streamBridge = streamBridge;
        this.clickHouseRepository = clickHouseRepository;
        this.redisTemplate = redisTemplate;
        this.alertsPublishedCounter = alertsPublishedCounter;
    }

    public void publish(AlertEvent event) {
        if (event == null) {
            return;
        }
        if ("INFO".equalsIgnoreCase(event.level()) && isRateLimited(event)) {
            return;
        }
        boolean sent = streamBridge.send(
                BINDING,
                MessageBuilder.withPayload(event)
                        .setHeader("contentType", "application/json")
                        .build()
        );
        if (!sent) {
            log.warn("Failed to publish alert to Kafka service={} level={}", event.service(), event.level());
            return;
        }
        alertsPublishedCounter.increment();
        try {
            clickHouseRepository.insertAppLog(event);
        } catch (Exception ex) {
            log.debug("ClickHouse app_logs insert skipped: {}", ex.getMessage());
        }
    }

    public void fromTelemetry(TelemetryEvent event) {
        if (event.status() >= 500) {
            publish(AlertEvent.of(
                    "ERROR",
                    event.service(),
                    "HTTP " + event.status() + " on " + event.endpoint()
                            + " latency=" + event.latencyMs() + "ms"
            ));
            return;
        }
        if (event.status() >= 400) {
            publish(AlertEvent.of(
                    "WARN",
                    event.service(),
                    "HTTP " + event.status() + " on " + event.endpoint()
                            + " latency=" + event.latencyMs() + "ms"
            ));
            return;
        }
        if (event.latencyMs() >= 800) {
            publish(AlertEvent.of(
                    "WARN",
                    event.service(),
                    "Elevated latency " + event.latencyMs() + "ms on " + event.endpoint()
            ));
            return;
        }
        // Sample successful traffic so the live panel stays alive without noise
        if (infoCounter.incrementAndGet() % 15 == 0) {
            publish(AlertEvent.of(
                    "INFO",
                    event.service(),
                    "Handled " + event.endpoint() + " latency=" + event.latencyMs() + "ms"
            ));
        }
    }

    public void fromMlPrediction(MlPredictionEvent event) {
        if (event.inferenceTimeMs() >= 60) {
            publish(AlertEvent.of(
                    "WARN",
                    event.modelId(),
                    "Slow inference " + event.inferenceTimeMs() + "ms prediction=" + event.prediction()
            ));
            return;
        }
        if (infoCounter.incrementAndGet() % 10 == 0) {
            publish(AlertEvent.of(
                    "INFO",
                    event.modelId(),
                    "Prediction=" + event.prediction() + " inference=" + event.inferenceTimeMs() + "ms"
            ));
        }
    }

    public void fromDrift(boolean datasetDrift, double driftShare, int driftedFeatures, int sampleSize) {
        if (datasetDrift) {
            publish(AlertEvent.of(
                    "ERROR",
                    "loan-risk-model",
                    "DATA DRIFT DETECTED share="
                            + String.format("%.0f", driftShare * 100)
                            + "% driftedFeatures=" + driftedFeatures
                            + " sampleSize=" + sampleSize
            ));
        } else {
            publish(AlertEvent.of(
                    "INFO",
                    "loan-risk-model",
                    "Drift check OK share="
                            + String.format("%.0f", driftShare * 100)
                            + "% sampleSize=" + sampleSize
            ));
        }
    }

    private boolean isRateLimited(AlertEvent event) {
        try {
            String key = RATE_KEY_PREFIX + event.service() + ":" + event.level();
            Boolean first = redisTemplate.opsForValue().setIfAbsent(key, "1", Duration.ofSeconds(2));
            return Boolean.FALSE.equals(first);
        } catch (Exception ex) {
            // Redis optional for correctness — never block alert path
            log.debug("Redis rate-limit unavailable: {}", ex.getMessage());
            return false;
        }
    }
}
