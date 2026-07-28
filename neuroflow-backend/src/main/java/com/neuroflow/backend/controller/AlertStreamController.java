package com.neuroflow.backend.controller;

import com.neuroflow.backend.service.AlertBroadcastService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/alerts")
public class AlertStreamController {

    private final AlertBroadcastService alertBroadcastService;

    public AlertStreamController(AlertBroadcastService alertBroadcastService) {
        this.alertBroadcastService = alertBroadcastService;
    }

    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() throws IOException {
        SseEmitter emitter = alertBroadcastService.subscribe();
        emitter.send(SseEmitter.event()
                .name("connected")
                .data(Map.of(
                        "status", "UP",
                        "source", "kafka-alerts",
                        "ts", Instant.now().toString()
                )));
        return emitter;
    }
}
