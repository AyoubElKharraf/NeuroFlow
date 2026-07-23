package com.neuroflow.backend.consumer;

import com.neuroflow.backend.model.MlPredictionEvent;
import com.neuroflow.backend.model.TelemetryEvent;
import com.neuroflow.backend.service.TelemetryIngestionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.function.Consumer;

@Configuration
public class EventConsumers {

    private static final Logger log = LoggerFactory.getLogger(EventConsumers.class);

    private final TelemetryIngestionService ingestionService;

    public EventConsumers(TelemetryIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @Bean
    public Consumer<TelemetryEvent> telemetryConsumer() {
        return event -> {
            log.debug("kafka_received topic=telemetry-events payload={}", event);
            ingestionService.ingestTelemetry(event);
        };
    }

    @Bean
    public Consumer<MlPredictionEvent> mlPredictionConsumer() {
        return event -> {
            log.debug("kafka_received topic=ml-predictions payload={}", event);
            ingestionService.ingestMlPrediction(event);
        };
    }
}
