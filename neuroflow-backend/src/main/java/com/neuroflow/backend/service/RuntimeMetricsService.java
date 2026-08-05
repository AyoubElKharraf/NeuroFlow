package com.neuroflow.backend.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
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
        double[] heap = resolveHeapBytes();
        Map<String, Object> jvm = new LinkedHashMap<>();
        jvm.put("heapUsedMb", bytesToMb(heap[0]));
        jvm.put("heapMaxMb", bytesToMb(heap[1]));
        jvm.put("heapUsagePct", round1(ratioPct(heap[0], heap[1])));
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

    /**
     * Micrometer {@code jvm.memory.max} is often {@code -1} under containers.
     * Prefer MemoryMXBean, then Runtime.maxMemory() as fallback.
     *
     * @return {@code [usedBytes, maxBytes]}
     */
    private double[] resolveHeapBytes() {
        MemoryUsage usage = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        double used = usage.getUsed();
        double max = usage.getMax();

        double micrometerUsed = gaugeValue("jvm.memory.used", "area", "heap");
        double micrometerMax = gaugeValue("jvm.memory.max", "area", "heap");
        if (micrometerUsed > 0) {
            used = micrometerUsed;
        }
        if (micrometerMax > 0) {
            max = micrometerMax;
        }
        if (max <= 0) {
            max = Runtime.getRuntime().maxMemory();
        }
        if (used <= 0) {
            Runtime rt = Runtime.getRuntime();
            used = Math.max(0L, rt.totalMemory() - rt.freeMemory());
        }
        return new double[]{used, max};
    }

    private double gaugeValue(String name) {
        var gauge = meterRegistry.find(name).gauge();
        return gauge == null ? 0.0 : gauge.value();
    }

    private double gaugeValue(String name, String tagKey, String tagValue) {
        var gauge = meterRegistry.find(name).tag(tagKey, tagValue).gauge();
        if (gauge == null) {
            return 0.0;
        }
        double value = gauge.value();
        if (Double.isNaN(value) || Double.isInfinite(value) || value < 0) {
            return 0.0;
        }
        return value;
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
