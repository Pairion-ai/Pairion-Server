package com.pairion.gateway.rest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

/** Tests for {@link AdapterController}. */
@WebMvcTest(AdapterController.class)
class AdapterControllerTest {

    @Autowired private MockMvc mockMvc;

    @Test
    void listAdaptersReturnsEmptyList() throws Exception {
        mockMvc.perform(get("/v1/adapters"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void getAdapterReturnsStub() throws Exception {
        mockMvc.perform(get("/v1/adapters/llm/anthropic"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category").value("llm"))
                .andExpect(jsonPath("$.name").value("anthropic"))
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.capabilities").exists());
    }
}
