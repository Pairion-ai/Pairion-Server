package com.pairion.gateway.rest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

/** Tests for {@link SkillController}. */
@WebMvcTest(SkillController.class)
class SkillControllerTest {

    @Autowired private MockMvc mockMvc;

    @Test
    void listSkillsReturnsEmptyList() throws Exception {
        mockMvc.perform(get("/v1/skills"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void getSkillReturnsStub() throws Exception {
        mockMvc.perform(get("/v1/skills/skill-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("skill-1"))
                .andExpect(jsonPath("$.name").value("Stub Skill"))
                .andExpect(jsonPath("$.description").exists())
                .andExpect(jsonPath("$.permittedUsers").isArray());
    }
}
