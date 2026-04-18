package com.pairion.gateway.rest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

/** Tests for {@link MemoryController}. */
@WebMvcTest(MemoryController.class)
class MemoryControllerTest {

    @Autowired private MockMvc mockMvc;

    @Test
    void listEpisodesReturnsEmptyList() throws Exception {
        mockMvc.perform(get("/v1/memory/episodes").param("userId", "user-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void listEpisodesWithLimit() throws Exception {
        mockMvc.perform(get("/v1/memory/episodes").param("userId", "user-1").param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }
}
