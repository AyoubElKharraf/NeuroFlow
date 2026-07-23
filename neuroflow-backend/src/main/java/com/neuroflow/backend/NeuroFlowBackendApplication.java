package com.neuroflow.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class NeuroFlowBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(NeuroFlowBackendApplication.class, args);
    }
}
