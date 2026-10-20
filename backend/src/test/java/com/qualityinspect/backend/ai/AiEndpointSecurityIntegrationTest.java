package com.qualityinspect.backend.ai;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The Quality Report Assistant must never answer an unauthenticated caller —
 * every evidence retrieval starts with authentication (authorized-data rule).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AiEndpointSecurityIntegrationTest {

    @Autowired MockMvc mockMvc;

    @Test
    void question_withoutToken_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/ai/questions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"What needs attention?\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void batchSummary_withoutToken_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/ai/reports/batch-summary")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"batchId\":1}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void qualityReport_withoutToken_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/ai/reports/quality-report")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void history_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/ai/reports"))
                .andExpect(status().isUnauthorized());
    }
}
