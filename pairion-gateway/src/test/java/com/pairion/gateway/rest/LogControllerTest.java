package com.pairion.gateway.rest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** Tests for {@link LogController}. */
@WebMvcTest(LogController.class)
class LogControllerTest {

    @Autowired private MockMvc mockMvc;

    @Test
    void postLogsReturns204() throws Exception {
        mockMvc.perform(
                        post("/v1/logs")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "[{\"timestamp\":\"2026-01-01T00:00:00Z\","
                                                + "\"level\":\"INFO\","
                                                + "\"message\":\"Client started\"}]"))
                .andExpect(status().isNoContent());
    }

    @Test
    void postLogsMultipleRecords() throws Exception {
        mockMvc.perform(
                        post("/v1/logs")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "[{\"timestamp\":\"2026-01-01T00:00:00Z\","
                                                + "\"level\":\"INFO\","
                                                + "\"message\":\"msg1\"},"
                                                + "{\"timestamp\":\"2026-01-01T00:00:01Z\","
                                                + "\"level\":\"WARN\","
                                                + "\"message\":\"msg2\"}]"))
                .andExpect(status().isNoContent());
    }

    @Test
    void postLogsEmptyArray() throws Exception {
        mockMvc.perform(post("/v1/logs").contentType(MediaType.APPLICATION_JSON).content("[]"))
                .andExpect(status().isNoContent());
    }
}
