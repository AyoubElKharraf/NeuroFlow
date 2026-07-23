package com.neuroflow.backend.controller;

import com.neuroflow.backend.repository.ClickHouseRepository;
import com.neuroflow.backend.service.SimulationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class SimulationController {

    private final SimulationService simulationService;
    private final ClickHouseRepository clickHouseRepository;

    public SimulationController(
            SimulationService simulationService,
            ClickHouseRepository clickHouseRepository) {
        this.simulationService = simulationService;
        this.clickHouseRepository = clickHouseRepository;
    }

    @PostMapping("/simulation/start")
    public Map<String, Object> start() {
        return simulationService.start();
    }

    @PostMapping("/simulation/stop")
    public Map<String, Object> stop() {
        return simulationService.stop();
    }

    @GetMapping("/simulation/status")
    public Map<String, Object> status() {
        return simulationService.status();
    }

    @GetMapping("/metrics/ingestion")
    public Map<String, Object> ingestionMetrics() {
        return Map.of(
                "telemetryEvents", clickHouseRepository.countTelemetry(),
                "mlPredictions", clickHouseRepository.countMlPredictions()
        );
    }
}
