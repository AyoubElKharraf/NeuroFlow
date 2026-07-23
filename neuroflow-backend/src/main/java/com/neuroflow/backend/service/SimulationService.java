package com.neuroflow.backend.service;

import com.neuroflow.backend.model.MlPredictionEvent;
import com.neuroflow.backend.model.TelemetryEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
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
    private static final int[] STATUS_CODES = {200, 200, 200, 200, 201, 400, 404, 500};

    private final StreamBridge streamBridge;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicLong emitted = new AtomicLong(0);

    public SimulationService(StreamBridge streamBridge) {
        this.streamBridge = streamBridge;
    }

    public Map<String, Object> start() {
        running.set(true);
        log.info("simulation_started rate=~5 msg/sec");
        return status();
    }

    public Map<String, Object> stop() {
        running.set(false);
        log.info("simulation_stopped emitted={}", emitted.get());
        return status();
    }

    public Map<String, Object> status() {
        return Map.of(
                "running", running.get(),
                "emitted", emitted.get(),
                "targetRatePerSec", 5
        );
    }

    @Scheduled(fixedRate = 200)
    public void tick() {
        if (!running.get()) {
            return;
        }
        TelemetryEvent telemetry = randomTelemetry();
        boolean sent = streamBridge.send(
                "telemetry-events",
                MessageBuilder.withPayload(telemetry).build()
        );
        if (sent) {
            emitted.incrementAndGet();
        } else {
            log.warn("Failed to publish telemetry event to Kafka");
        }

        if (emitted.get() % 5 == 0) {
            MlPredictionEvent ml = randomMlPrediction();
            streamBridge.send("ml-predictions", MessageBuilder.withPayload(ml).build());
        }
    }

    private TelemetryEvent randomTelemetry() {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        String service = SERVICES.get(rnd.nextInt(SERVICES.size()));
        String endpoint = ENDPOINTS.get(rnd.nextInt(ENDPOINTS.size()));
        int status = STATUS_CODES[rnd.nextInt(STATUS_CODES.length)];
        long latency = switch (status) {
            case 500 -> rnd.nextLong(800, 2500);
            case 400, 404 -> rnd.nextLong(20, 120);
            default -> rnd.nextLong(15, 350);
        };
        return new TelemetryEvent(service, latency, status, Instant.now(), endpoint);
    }

    private MlPredictionEvent randomMlPrediction() {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        double confidence = rnd.nextDouble(0.55, 0.99);
        return new MlPredictionEvent(
                "fraud-classifier",
                "v1.2.0",
                rnd.nextLong(5, 80),
                rnd.nextBoolean() ? "fraud" : "legit",
                Map.of(
                        "amount", rnd.nextDouble(5, 500),
                        "merchant_risk", rnd.nextDouble(0, 1),
                        "confidence", confidence
                ),
                Instant.now()
        );
    }
}
