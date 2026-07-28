package com.neuroflow.backend.controller;

import com.neuroflow.backend.service.AnalyticsClientService;
import com.neuroflow.backend.service.WebhookNotificationService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/analytics")
public class AnalyticsController {

    private final AnalyticsClientService analyticsClientService;
    private final WebhookNotificationService webhookNotificationService;

    public AnalyticsController(
            AnalyticsClientService analyticsClientService,
            WebhookNotificationService webhookNotificationService) {
        this.analyticsClientService = analyticsClientService;
        this.webhookNotificationService = webhookNotificationService;
    }

    @PostMapping("/drift/run")
    public Map<String, Object> runDriftCheck() {
        return analyticsClientService.runDriftCheck("manual");
    }

    @GetMapping("/drift/history")
    public Map<String, Object> driftHistory(
            @RequestParam(name = "limit", defaultValue = "30") int limit) {
        return analyticsClientService.driftHistory(limit);
    }

    @GetMapping("/webhooks/status")
    public Map<String, Object> webhookStatus() {
        return webhookNotificationService.status();
    }

    @PostMapping("/webhooks/test")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> webhookTest() {
        return webhookNotificationService.sendTest();
    }
}
