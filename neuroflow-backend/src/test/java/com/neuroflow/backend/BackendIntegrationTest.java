package com.neuroflow.backend;

import com.neuroflow.backend.model.TelemetryEvent;
import com.neuroflow.backend.repository.ClickHouseRepository;
import com.neuroflow.backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

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

    @Test
    void contextLoadsAgainstTestcontainers() {
        Integer one = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
        assertThat(one).isEqualTo(1);
    }

    @Test
    void healthEndpointIsUp() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
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
}
