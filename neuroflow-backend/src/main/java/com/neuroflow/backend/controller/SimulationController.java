package com.neuroflow.backend.controller;

import com.neuroflow.backend.service.SimulationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class SimulationController {

    private final SimulationService simulationService;

    public SimulationController(SimulationService simulationService) {
        this.simulationService = simulationService;
    }

    @PostMapping("/simulation/start")
    public Map<String, Object> start() {
        return simulationService.start();
    }

    @PostMapping("/simulation/stop")
    public Map<String, Object> stop() {
        return simulationService.stop();
    }

    @PostMapping("/simulation/error-rate")
    public Map<String, Object> setErrorRate(@RequestBody Map<String, Object> body) {
        Object raw = body == null ? null : body.get("errorRatePct");
        int pct = 8;
        if (raw instanceof Number number) {
            pct = number.intValue();
        } else if (raw instanceof String text && !text.isBlank()) {
            pct = Integer.parseInt(text.trim());
        }
        return simulationService.setErrorRatePct(pct);
    }

    @GetMapping("/simulation/status")
    public Map<String, Object> status() {
        return simulationService.status();
    }
}
