package com.neuroflow.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.neuroflow.backend.model.MlPredictionEvent;
import com.neuroflow.backend.model.TelemetryEvent;
import io.micrometer.core.instrument.Counter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.http.MediaType;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class SimulationService {

    private static final Logger log = LoggerFactory.getLogger(SimulationService.class);

    private static final List<String> SERVICES = List.of(
            "checkout-api", "auth-api", "catalog-api", "payments-api"
    );
    private static final List<String> ENDPOINTS = List.of(
            "/api/orders", "/api/login", "/api/products", "/api/payments/charge"
    );
    private static final int[] ERROR_STATUS_CODES = {400, 404, 500};
    /** Demo-friendly default (~8% HTTP errors instead of ~37%). */
    private static final int DEFAULT_ERROR_RATE_PCT = 8;

    private final StreamBridge streamBridge;
    private final RestClient analyticsRestClient;
    private final Counter simulationEmittedCounter;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicLong emitted = new AtomicLong();
    private final AtomicInteger errorRatePct = new AtomicInteger(DEFAULT_ERROR_RATE_PCT);

    public SimulationService(
            StreamBridge streamBridge,
            RestClient analyticsRestClient,
            @Qualifier("simulationEmittedCounter") Counter simulationEmittedCounter) {
        this.streamBridge = streamBridge;
        this.analyticsRestClient = analyticsRestClient;
        this.simulationEmittedCounter = simulationEmittedCounter;
    }

    public Map<String, Object> start() {
        emitted.set(0);
        running.set(true);
        log.info("simulation_started rate=~5 msg/sec errorRatePct={}", errorRatePct.get());
        return status();
    }

    public Map<String, Object> stop() {
        running.set(false);
        log.info("simulation_stopped emitted={}", emitted.get());
        return status();
    }

    public Map<String, Object> setErrorRatePct(int pct) {
        int clamped = Math.max(0, Math.min(100, pct));
        errorRatePct.set(clamped);
        log.info("simulation_error_rate_pct={}", clamped);
        return status();
    }

    public Map<String, Object> status() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("running", running.get());
        body.put("emitted", emitted.get());
        body.put("targetRatePerSec", 5);
        body.put("errorRatePct", errorRatePct.get());
        return body;
    }

    @Scheduled(fixedRate = 200)
    public void tick() {
        if (!running.get()) {
            return;
        }
        TelemetryEvent telemetry = randomTelemetry();
        boolean sent = streamBridge.send(
                "telemetry-events-out",
                MessageBuilder.withPayload(telemetry)
                        .setHeader("contentType", "application/json")
                        .build()
        );
        if (sent) {
            emitted.incrementAndGet();
            simulationEmittedCounter.increment();
        } else {
            log.warn("Failed to publish telemetry event to Kafka");
        }

        if (emitted.get() % 5 == 0) {
            MlPredictionEvent ml = randomMlPrediction();
            streamBridge.send(
                    "ml-predictions-out",
                    MessageBuilder.withPayload(ml)
                            .setHeader("contentType", "application/json")
                            .build()
            );
        }
    }

    private TelemetryEvent randomTelemetry() {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        String service = SERVICES.get(rnd.nextInt(SERVICES.size()));
        String endpoint = ENDPOINTS.get(rnd.nextInt(ENDPOINTS.size()));
        int status = randomHttpStatus(rnd);
        long latency = switch (status) {
            case 500 -> rnd.nextLong(800, 2500);
            case 400, 404 -> rnd.nextLong(20, 120);
            default -> rnd.nextLong(15, 350);
        };
        return new TelemetryEvent(service, latency, status, Instant.now(), endpoint);
    }

    private int randomHttpStatus(ThreadLocalRandom rnd) {
        if (rnd.nextInt(100) < errorRatePct.get()) {
            return ERROR_STATUS_CODES[rnd.nextInt(ERROR_STATUS_CODES.length)];
        }
        return rnd.nextBoolean() ? 200 : 201;
    }

    private MlPredictionEvent randomMlPrediction() {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        double age = clamp(rnd.nextGaussian() * 12.0 + 42.0, 18.0, 80.0);
        double income = clamp(Math.exp(rnd.nextGaussian() * 0.45 + 10.5), 15_000.0, 250_000.0);
        double creditScore = clamp(rnd.nextGaussian() * 55.0 + 680.0, 300.0, 850.0);
        double loanAmount = clamp(Math.exp(rnd.nextGaussian() * 0.55 + 9.5), 1_000.0, 150_000.0);

        Map<String, Object> features = new HashMap<>();
        features.put("age", Math.round(age * 10.0) / 10.0);
        features.put("income", Math.round(income * 100.0) / 100.0);
        features.put("credit_score", Math.round(creditScore * 10.0) / 10.0);
        features.put("loan_amount", Math.round(loanAmount * 100.0) / 100.0);

        String prediction = "approve";
        double confidence = 0.7;
        long inferenceMs = rnd.nextLong(5, 40);

        try {
            long started = System.nanoTime();
            JsonNode response = analyticsRestClient.post()
                    .uri("/api/v1/predict")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "age", features.get("age"),
                            "income", features.get("income"),
                            "credit_score", features.get("credit_score"),
                            "loan_amount", features.get("loan_amount")
                    ))
                    .retrieve()
                    .body(JsonNode.class);
            inferenceMs = Math.max(1L, (System.nanoTime() - started) / 1_000_000L);
            if (response != null) {
                prediction = response.path("prediction").asText("approve");
                confidence = response.path("confidence").asDouble(0.7);
                String version = response.path("model_version").asText("v1.0.0");
                features.put("confidence", confidence);
                return new MlPredictionEvent(
                        "loan-risk-model",
                        version,
                        inferenceMs,
                        prediction,
                        features,
                        Instant.now()
                );
            }
        } catch (RestClientException ex) {
            log.debug("Analytics predict unavailable, using local fallback: {}", ex.getMessage());
            confidence = rnd.nextDouble(0.55, 0.99);
            prediction = rnd.nextBoolean() ? "approve" : "reject";
            inferenceMs = rnd.nextLong(5, 80);
        }

        features.put("confidence", confidence);
        return new MlPredictionEvent(
                "loan-risk-model",
                "v1.0.0",
                inferenceMs,
                prediction,
                features,
                Instant.now()
        );
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
