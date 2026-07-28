package com.neuroflow.backend.service;

import com.neuroflow.backend.repository.ClickHouseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Persists rolling KPI snapshots into ClickHouse {@code metric_aggregates}.
 */
@Service
public class MetricAggregateScheduler {

    private static final Logger log = LoggerFactory.getLogger(MetricAggregateScheduler.class);

    private final MetricsService metricsService;
    private final ClickHouseRepository clickHouseRepository;

    public MetricAggregateScheduler(
            MetricsService metricsService,
            ClickHouseRepository clickHouseRepository) {
        this.metricsService = metricsService;
        this.clickHouseRepository = clickHouseRepository;
    }

    @Scheduled(fixedDelayString = "${neuroflow.metrics.aggregate-interval-ms:60000}")
    public void persistWindow() {
        try {
            Map<String, Object> kpis = metricsService.getKpis();
            clickHouseRepository.insertMetricAggregates(kpis);
            log.debug("Persisted metric aggregates from ClickHouse KPIs");
        } catch (Exception ex) {
            log.debug("Metric aggregate persistence skipped: {}", ex.getMessage());
        }
    }
}
