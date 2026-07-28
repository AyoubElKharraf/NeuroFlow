package com.neuroflow.backend.controller;

import com.neuroflow.backend.service.MetricsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/metrics")
public class MetricsController {

    private final MetricsService metricsService;

    public MetricsController(MetricsService metricsService) {
        this.metricsService = metricsService;
    }

    @GetMapping("/ingestion")
    public Map<String, Object> ingestionMetrics() {
        return metricsService.getIngestionCounts();
    }

    @GetMapping("/kpis")
    public Map<String, Object> kpis() {
        return metricsService.getKpis();
    }

    @GetMapping("/performance")
    public Map<String, Object> performance(
            @RequestParam(name = "minutes", defaultValue = "30") int minutes) {
        return metricsService.getPerformanceSeries(minutes);
    }
}
