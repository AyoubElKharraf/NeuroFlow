package com.neuroflow.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@RestController
@RequestMapping("/api/v1/alerts")
public class AlertStreamController {

    private static final Logger log = LoggerFactory.getLogger(AlertStreamController.class);
    private static final List<String> SERVICES = List.of(
            "checkout-api", "auth-api", "catalog-api", "payments-api", "loan-risk-model"
    );
    private static final String[] LEVELS = {"INFO", "INFO", "INFO", "WARN", "ERROR"};

    private final ObjectMapper objectMapper;
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private final AtomicLong sequence = new AtomicLong();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "neuroflow-sse");
        t.setDaemon(true);
        return t;
    });

    public AlertStreamController(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        scheduler.scheduleAtFixedRate(this::broadcastTick, 1, 1, TimeUnit.SECONDS);
    }

    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        SseEmitter emitter = new SseEmitter(0L);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(ex -> emitters.remove(emitter));
        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data(Map.of("status", "UP", "ts", Instant.now().toString())));
        } catch (IOException ex) {
            emitters.remove(emitter);
        }
        return emitter;
    }

    private void broadcastTick() {
        if (emitters.isEmpty()) {
            return;
        }
        long idx = sequence.incrementAndGet();
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        String level = LEVELS[rnd.nextInt(LEVELS.length)];
        String service = SERVICES.get(rnd.nextInt(SERVICES.size()));
        String message = switch (level) {
            case "ERROR" -> "Upstream timeout after retries";
            case "WARN" -> "Elevated p95 latency window";
            default -> "Handled request latency=" + rnd.nextInt(15, 120) + "ms";
        };

        Map<String, Object> payload = Map.of(
                "id", UUID.randomUUID().toString(),
                "timestamp", Instant.now().toString(),
                "level", level,
                "service", service,
                "message", message + " seq=" + idx
        );

        try {
            String json = objectMapper.writeValueAsString(payload);
            for (SseEmitter emitter : emitters) {
                try {
                    emitter.send(SseEmitter.event().data(json));
                } catch (IOException ex) {
                    emitters.remove(emitter);
                }
            }
        } catch (Exception ex) {
            log.debug("SSE broadcast skipped: {}", ex.getMessage());
        }
    }
}
