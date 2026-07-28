package com.neuroflow.backend.service;

import com.neuroflow.backend.repository.ClickHouseRepository;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds dashboard KPIs and performance series from ClickHouse aggregates.
 */
@Service
public class MetricsService {

    private final ClickHouseRepository clickHouseRepository;
    private final AnalyticsClientService analyticsClientService;

    public MetricsService(
            ClickHouseRepository clickHouseRepository,
            AnalyticsClientService analyticsClientService) {
        this.clickHouseRepository = clickHouseRepository;
        this.analyticsClientService = analyticsClientService;
    }

    public Map<String, Object> getIngestionCounts() {
        return Map.of(
                "telemetryEvents", clickHouseRepository.countTelemetry(),
                "mlPredictions", clickHouseRepository.countMlPredictions()
        );
    }

    public Map<String, Object> getKpis() {
        long telemetry = clickHouseRepository.countTelemetry();
        long ml = clickHouseRepository.countMlPredictions();
        Map<String, Double> latency = clickHouseRepository.fetchLatencyPercentiles(5);
        double errorRate = clickHouseRepository.fetchErrorRatePct(1);
        double trend = clickHouseRepository.fetchEventsTrendPct(5);
        Map<String, Object> lastDrift = analyticsClientService.lastDriftSummary();

        Map<String, Object> kpis = new LinkedHashMap<>();
        kpis.put("totalEvents", telemetry + ml);
        kpis.put("telemetryEvents", telemetry);
        kpis.put("mlPredictions", ml);
        kpis.put("eventsTrendPct", round1(trend));
        kpis.put("latencyP95Ms", Math.round(latency.getOrDefault("p95", 0.0)));
        kpis.put("latencyP99Ms", Math.round(latency.getOrDefault("p99", 0.0)));
        kpis.put("errorRatePct", round1(errorRate));
        kpis.put("datasetDrift", Boolean.TRUE.equals(lastDrift.get("datasetDrift")));
        kpis.put("driftShare", lastDrift.getOrDefault("driftShare", 0.0));
        kpis.put("source", "clickhouse");
        return kpis;
    }

    public Map<String, Object> getPerformanceSeries(int minutes) {
        int window = Math.max(5, Math.min(minutes, 180));
        List<Map<String, Object>> series = clickHouseRepository.fetchPerformanceSeries(window);
        return Map.of(
                "minutes", window,
                "source", "clickhouse",
                "series", series
        );
    }

    private static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
