package com.neuroflow.backend.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

import java.lang.management.ManagementFactory;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Aggregates Micrometer / Actuator runtime metrics for the dashboard JSON API.
 */
@Service
public class RuntimeMetricsService {

    private final MeterRegistry meterRegistry;

    public RuntimeMetricsService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public Map<String, Object> snapshot() {
        Map<String, Object> jvm = new LinkedHashMap<>();
        jvm.put("heapUsedMb", bytesToMb(gaugeValue("jvm.memory.used", "area", "heap")));
        jvm.put("heapMaxMb", bytesToMb(gaugeValue("jvm.memory.max", "area", "heap")));
        jvm.put("heapUsagePct", round1(ratioPct(
                gaugeValue("jvm.memory.used", "area", "heap"),
                gaugeValue("jvm.memory.max", "area", "heap")
        )));
        jvm.put("threadsLive", (long) gaugeValue("jvm.threads.live"));
        jvm.put("cpuProcessPct", round1(gaugeValue("process.cpu.usage") * 100.0));
        jvm.put("cpuSystemPct", round1(gaugeValue("system.cpu.usage") * 100.0));
        jvm.put("uptimeSec", ManagementFactory.getRuntimeMXBean().getUptime() / 1000.0);

        Map<String, Object> http = new LinkedHashMap<>();
        Timer httpTimer = meterRegistry.find("http.server.requests").timer();
        if (httpTimer != null) {
            http.put("requestCount", httpTimer.count());
            http.put("meanLatencyMs", round1(httpTimer.mean(TimeUnit.MILLISECONDS)));
            http.put("maxLatencyMs", round1(httpTimer.max(TimeUnit.MILLISECONDS)));
        } else {
            http.put("requestCount", 0L);
            http.put("meanLatencyMs", 0.0);
            http.put("maxLatencyMs", 0.0);
        }

        Map<String, Object> neuroflow = new LinkedHashMap<>();
        neuroflow.put("alertsPublished", counterValue("neuroflow.alerts.published"));
        neuroflow.put("simulationEmitted", counterValue("neuroflow.simulation.emitted"));
        neuroflow.put("driftChecks", counterValue("neuroflow.drift.checks"));
        neuroflow.put("driftDetected", counterValue("neuroflow.drift.detected"));

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("source", "actuator-micrometer");
        snapshot.put("prometheusPath", "/actuator/prometheus");
        snapshot.put("jvm", jvm);
        snapshot.put("http", http);
        snapshot.put("neuroflow", neuroflow);
        return snapshot;
    }

    private double gaugeValue(String name) {
        var gauge = meterRegistry.find(name).gauge();
        return gauge == null ? 0.0 : gauge.value();
    }

    private double gaugeValue(String name, String tagKey, String tagValue) {
        var gauge = meterRegistry.find(name).tag(tagKey, tagValue).gauge();
        return gauge == null ? 0.0 : gauge.value();
    }

    private long counterValue(String name) {
        Counter counter = meterRegistry.find(name).counter();
        return counter == null ? 0L : Math.round(counter.count());
    }

    private static double bytesToMb(double bytes) {
        if (bytes <= 0) {
            return 0.0;
        }
        return Math.round((bytes / (1024.0 * 1024.0)) * 10.0) / 10.0;
    }

    private static double ratioPct(double used, double max) {
        if (max <= 0) {
            return 0.0;
        }
        return (used / max) * 100.0;
    }

    private static double round1(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0.0;
        }
        return Math.round(value * 10.0) / 10.0;
    }
}
