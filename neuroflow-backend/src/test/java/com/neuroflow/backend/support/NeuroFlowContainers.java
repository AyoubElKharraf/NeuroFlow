package com.neuroflow.backend.support;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

/**
 * Shared Testcontainers used by Phase 5 integration tests.
 * Started once per JVM via {@link org.testcontainers.junit.jupiter.Testcontainers}.
 */
public final class NeuroFlowContainers {

    public static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("neuroflow_db")
            .withUsername("neuroflow")
            .withPassword("neuroflow")
            .withInitScript("db/init-postgres.sql");

    public static final KafkaContainer KAFKA = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));

    @SuppressWarnings("resource")
    public static final GenericContainer<?> REDIS = new GenericContainer<>(
            DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379)
            .waitingFor(Wait.forLogMessage(".*Ready to accept connections.*", 1));

    @SuppressWarnings("resource")
    public static final GenericContainer<?> CLICKHOUSE = new GenericContainer<>(
            DockerImageName.parse("clickhouse/clickhouse-server:24.8"))
            .withExposedPorts(8123)
            .withEnv("CLICKHOUSE_DB", "neuroflow_analytics")
            .withEnv("CLICKHOUSE_USER", "neuroflow")
            .withEnv("CLICKHOUSE_PASSWORD", "neuroflow")
            .withEnv("CLICKHOUSE_DEFAULT_ACCESS_MANAGEMENT", "1")
            .withCopyFileToContainer(
                    MountableFile.forClasspathResource("db/init-clickhouse.sql"),
                    "/docker-entrypoint-initdb.d/init.sql")
            .waitingFor(Wait.forHttp("/ping").forPort(8123).forStatusCode(200)
                    .withStartupTimeout(Duration.ofMinutes(2)));

    private static volatile boolean clickHouseSchemaReady = false;

    private NeuroFlowContainers() {
    }

    public static void startAll() {
        POSTGRES.start();
        KAFKA.start();
        REDIS.start();
        CLICKHOUSE.start();
        ensureClickHouseSchema();
    }

    public static String clickHouseJdbcUrl() {
        return "jdbc:clickhouse://" + CLICKHOUSE.getHost() + ":"
                + CLICKHOUSE.getMappedPort(8123) + "/neuroflow_analytics";
    }

    /**
     * ClickHouse entrypoint scripts are not always applied the same way as Postgres.
     * Apply schema idempotently over HTTP after the container is healthy.
     */
    public static synchronized void ensureClickHouseSchema() {
        if (clickHouseSchemaReady) {
            return;
        }
        try {
            String sql = new String(
                    NeuroFlowContainers.class.getClassLoader()
                            .getResourceAsStream("db/init-clickhouse.sql")
                            .readAllBytes(),
                    StandardCharsets.UTF_8
            );
            String endpoint = "http://" + CLICKHOUSE.getHost() + ":"
                    + CLICKHOUSE.getMappedPort(8123) + "/";
            String basic = Base64.getEncoder()
                    .encodeToString("neuroflow:neuroflow".getBytes(StandardCharsets.UTF_8));

            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();

            for (String statement : sql.split(";")) {
                String trimmed = statement.strip();
                if (trimmed.isEmpty() || trimmed.startsWith("--")) {
                    continue;
                }
                // Drop pure comment-only chunks
                StringBuilder executable = new StringBuilder();
                for (String line : trimmed.split("\\R")) {
                    String cleaned = line.strip();
                    if (!cleaned.isEmpty() && !cleaned.startsWith("--")) {
                        if (!executable.isEmpty()) {
                            executable.append('\n');
                        }
                        executable.append(cleaned);
                    }
                }
                if (executable.isEmpty()) {
                    continue;
                }

                HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                        .timeout(Duration.ofSeconds(30))
                        .header("Authorization", "Basic " + basic)
                        .POST(HttpRequest.BodyPublishers.ofString(executable.toString()))
                        .build();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() >= 400) {
                    throw new IllegalStateException(
                            "ClickHouse schema init failed HTTP " + response.statusCode()
                                    + ": " + response.body());
                }
            }
            clickHouseSchemaReady = true;
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to initialize ClickHouse schema", ex);
        }
    }
}
