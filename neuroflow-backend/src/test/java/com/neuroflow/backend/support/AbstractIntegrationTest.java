package com.neuroflow.backend.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
public abstract class AbstractIntegrationTest {

    static {
        // Docker Desktop on Windows often exposes the engine on dockerDesktopLinuxEngine,
        // while Testcontainers defaults to docker_engine / docker_cli and fails with HTTP 400.
        if (System.getProperty("os.name", "").toLowerCase().contains("win")
                && System.getenv("DOCKER_HOST") == null
                && System.getProperty("DOCKER_HOST") == null) {
            System.setProperty("DOCKER_HOST", "npipe:////./pipe/dockerDesktopLinuxEngine");
        }
        NeuroFlowContainers.startAll();
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", NeuroFlowContainers.POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", NeuroFlowContainers.POSTGRES::getUsername);
        registry.add("spring.datasource.password", NeuroFlowContainers.POSTGRES::getPassword);

        registry.add("spring.data.redis.host", NeuroFlowContainers.REDIS::getHost);
        registry.add("spring.data.redis.port",
                () -> NeuroFlowContainers.REDIS.getMappedPort(6379));

        registry.add("spring.cloud.stream.kafka.binder.brokers",
                NeuroFlowContainers.KAFKA::getBootstrapServers);
        registry.add("spring.kafka.bootstrap-servers",
                NeuroFlowContainers.KAFKA::getBootstrapServers);

        registry.add("neuroflow.clickhouse.url", NeuroFlowContainers::clickHouseJdbcUrl);
        registry.add("neuroflow.clickhouse.username", () -> "neuroflow");
        registry.add("neuroflow.clickhouse.password", () -> "neuroflow");
        registry.add("neuroflow.analytics.drift-check-enabled", () -> "false");
        registry.add("neuroflow.analytics.base-url", () -> "http://127.0.0.1:9");
    }
}
