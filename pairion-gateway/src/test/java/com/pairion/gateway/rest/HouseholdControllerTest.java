package com.pairion.gateway.rest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** Tests for {@link HouseholdController}. */
@WebMvcTest(HouseholdController.class)
class HouseholdControllerTest {

    @Autowired private MockMvc mockMvc;

    @Test
    void getHouseholdReturnsSummary() throws Exception {
        mockMvc.perform(get("/v1/household"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.users").isArray());
    }

    @Test
    void listUsersReturnsEmptyList() throws Exception {
        mockMvc.perform(get("/v1/household/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void createUserReturns201() throws Exception {
        mockMvc.perform(
                        post("/v1/household/users")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"role\":\"MEMBER\",\"displayName\":\"Test User\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.role").value("MEMBER"))
                .andExpect(jsonPath("$.displayName").value("Test User"))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.enrollmentComplete").value(false));
    }

    @Test
    void getUserReturnsStub() throws Exception {
        mockMvc.perform(get("/v1/household/users/user-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("user-123"))
                .andExpect(jsonPath("$.role").value("MEMBER"))
                .andExpect(jsonPath("$.displayName").value("Stub User"));
    }

    @Test
    void deleteUserReturns204() throws Exception {
        mockMvc.perform(delete("/v1/household/users/user-123")).andExpect(status().isNoContent());
    }
}
