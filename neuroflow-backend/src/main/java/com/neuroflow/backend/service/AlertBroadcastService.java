package com.neuroflow.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neuroflow.backend.model.AlertEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Fan-out hub for authenticated SSE clients. Fed by Kafka {@code alerts} consumer
 * (and heartbeat), not by synthetic random ticks.
 */
@Service
public class AlertBroadcastService {

    private static final Logger log = LoggerFactory.getLogger(AlertBroadcastService.class);

    private final ObjectMapper objectMapper;
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public AlertBroadcastService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(0L);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(ex -> emitters.remove(emitter));
        return emitter;
    }

    public void broadcast(AlertEvent event) {
        if (emitters.isEmpty() || event == null) {
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(event);
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

    public int subscriberCount() {
        return emitters.size();
    }
}
