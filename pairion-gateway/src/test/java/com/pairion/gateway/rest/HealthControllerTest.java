package com.pairion.gateway.rest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

/** Tests for {@link HealthController}. */
@WebMvcTest(HealthController.class)
class HealthControllerTest {

    @Autowired private MockMvc mockMvc;

    @Test
    void getHealthReturnsHealthy() throws Exception {
        mockMvc.perform(get("/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("healthy"));
    }

    @Test
    void getVersionReturnsVersionInfo() throws Exception {
        mockMvc.perform(get("/v1/version"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value("0.1.0"))
                .andExpect(jsonPath("$.buildTime").exists())
                .andExpect(jsonPath("$.gitCommit").value("development"));
    }
}
