package com.neuroflow.backend.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MetricsConfig {

    @Bean
    Counter alertsPublishedCounter(MeterRegistry registry) {
        return Counter.builder("neuroflow.alerts.published")
                .description("Kafka alerts published by NeuroFlow")
                .register(registry);
    }

    @Bean
    Counter simulationEmittedCounter(MeterRegistry registry) {
        return Counter.builder("neuroflow.simulation.emitted")
                .description("Telemetry events emitted by the simulation")
                .register(registry);
    }

    @Bean
    Counter driftChecksCounter(MeterRegistry registry) {
        return Counter.builder("neuroflow.drift.checks")
                .description("Drift checks executed")
                .register(registry);
    }

    @Bean
    Counter driftDetectedCounter(MeterRegistry registry) {
        return Counter.builder("neuroflow.drift.detected")
                .description("Drift checks that detected dataset drift")
                .register(registry);
    }
}
