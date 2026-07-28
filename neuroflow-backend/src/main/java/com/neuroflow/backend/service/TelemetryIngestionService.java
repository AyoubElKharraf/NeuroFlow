package com.neuroflow.backend.service;

import com.neuroflow.backend.model.MlPredictionEvent;
import com.neuroflow.backend.model.TelemetryEvent;
import com.neuroflow.backend.repository.ClickHouseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class TelemetryIngestionService {

    private static final Logger log = LoggerFactory.getLogger(TelemetryIngestionService.class);

    private final ClickHouseRepository clickHouseRepository;
    private final AlertPublisherService alertPublisherService;

    public TelemetryIngestionService(
            ClickHouseRepository clickHouseRepository,
            AlertPublisherService alertPublisherService) {
        this.clickHouseRepository = clickHouseRepository;
        this.alertPublisherService = alertPublisherService;
    }

    public void ingestTelemetry(TelemetryEvent event) {
        clickHouseRepository.insertTelemetry(event);
        alertPublisherService.fromTelemetry(event);
        log.info(
                "telemetry_ingested service={} endpoint={} status={} latencyMs={}",
                event.service(),
                event.endpoint(),
                event.status(),
                event.latencyMs()
        );
    }

    public void ingestMlPrediction(MlPredictionEvent event) {
        clickHouseRepository.insertMlPrediction(event);
        alertPublisherService.fromMlPrediction(event);
        log.info(
                "ml_prediction_ingested model={} version={} inferenceMs={} prediction={}",
                event.modelId(),
                event.modelVersion(),
                event.inferenceTimeMs(),
                event.prediction()
        );
    }
}
