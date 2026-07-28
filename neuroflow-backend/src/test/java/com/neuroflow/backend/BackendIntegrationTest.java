package com.neuroflow.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neuroflow.backend.model.TelemetryEvent;
import com.neuroflow.backend.repository.ClickHouseRepository;
import com.neuroflow.backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BackendIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ClickHouseRepository clickHouseRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void contextLoadsAgainstTestcontainers() {
        Integer one = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
        assertThat(one).isEqualTo(1);
    }

    @Test
    void healthEndpointIsUpWithoutAuth() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void loginReturnsJwtForAdmin() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"admin","password":"admin123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.roles").isArray());
    }

    @Test
    void metricsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/metrics/ingestion"))
                .andExpect(status().isForbidden());
    }

    @Test
    void metricsAcceptBearerToken() throws Exception {
        String token = loginAs("admin", "admin123");

        mockMvc.perform(get("/api/v1/metrics/ingestion")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.telemetryEvents").isNumber());
    }

    @Test
    void kpisAndPerformanceComeFromClickHouse() throws Exception {
        String token = loginAs("admin", "admin123");

        clickHouseRepository.insertTelemetry(new TelemetryEvent(
                "kpi-service",
                55L,
                200,
                Instant.now(),
                "/api/orders"
        ));
        clickHouseRepository.insertTelemetry(new TelemetryEvent(
                "kpi-service",
                900L,
                500,
                Instant.now(),
                "/api/orders"
        ));

        mockMvc.perform(get("/api/v1/metrics/kpis")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalEvents").isNumber())
                .andExpect(jsonPath("$.latencyP95Ms").isNumber())
                .andExpect(jsonPath("$.latencyP99Ms").isNumber())
                .andExpect(jsonPath("$.errorRatePct").isNumber())
                .andExpect(jsonPath("$.source").value("clickhouse"));

        mockMvc.perform(get("/api/v1/metrics/performance")
                        .param("minutes", "30")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("clickhouse"))
                .andExpect(jsonPath("$.series").isArray())
                .andExpect(jsonPath("$.series.length()").value(30));
    }

    @Test
    void driftRunWithoutMlRowsReturnsSkipped() throws Exception {
        String token = loginAs("admin", "admin123");

        mockMvc.perform(post("/api/v1/analytics/drift/run")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SKIPPED"))
                .andExpect(jsonPath("$.features").isArray());
    }

    @Test
    void sseAllowsTokenQueryParam() throws Exception {
        String token = loginAs("user", "user123");

        mockMvc.perform(get("/api/v1/alerts/stream").param("token", token))
                .andExpect(status().isOk());
    }

    @Test
    void clickHouseInsertAndCountWorks() {
        long before = clickHouseRepository.countTelemetry();

        clickHouseRepository.insertTelemetry(new TelemetryEvent(
                "phase5-test",
                42L,
                200,
                Instant.now(),
                "/api/v1/health"
        ));

        long after = clickHouseRepository.countTelemetry();
        assertThat(after).isGreaterThan(before);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void simulationLifecycleEndpointsRespond() throws Exception {
        mockMvc.perform(post("/api/v1/simulation/start"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.running").value(true));

        mockMvc.perform(get("/api/v1/simulation/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.running").value(true));

        mockMvc.perform(post("/api/v1/simulation/stop"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.running").value(false));
    }

    private String loginAs(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"%s"}
                                """.formatted(username, password)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("accessToken").asText();
    }
}
