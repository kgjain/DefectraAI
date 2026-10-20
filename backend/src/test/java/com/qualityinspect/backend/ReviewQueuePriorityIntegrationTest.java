package com.qualityinspect.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qualityinspect.backend.user.RoleEntity;
import com.qualityinspect.backend.user.RoleRepository;
import com.qualityinspect.backend.user.UserEntity;
import com.qualityinspect.backend.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Innovation evidence: the Smart Review Prioritization queue must rank an
 * inspection with failed AI processing above a healthy one, and expose the
 * exact scoring reasons so the ordering is auditable.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ReviewQueuePriorityIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired RoleRepository roleRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired com.qualityinspect.backend.inspection.InspectionImageRepository imageRepository;
    @Autowired com.qualityinspect.backend.ml.ProcessingAttemptRepository attemptRepository;
    @Autowired com.qualityinspect.backend.ml.ModelResultRepository modelResultRepository;

    @Test
    void failedProcessing_inspection_ranksAbove_healthyInspection_withExplainableReasons() throws Exception {
        RoleEntity inspectorRole = roleRepository.findByCode("INSPECTOR").orElseThrow();
        RoleEntity managerRole = roleRepository.findByCode("QUALITY_MANAGER").orElseThrow();
        upsert("queue_inspector", "Inspector!123", inspectorRole);
        UserEntity manager = upsert("queue_manager", "Manager!12345", managerRole);

        String inspectorToken = login("queue_inspector", "Inspector!123");
        String managerToken = login("queue_manager", "Manager!12345");

        long suffix = System.nanoTime() % 1_000_000_000L;

        JsonNode product = json(post("/api/v1/products")
                .header("Authorization", "Bearer " + managerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"QUEUE-P-" + suffix + "\",\"name\":\"Queue Widget\",\"category\":\"PARTS\"}"));
        long productId = product.get("id").asLong();

        JsonNode batch = json(post("/api/v1/batches")
                .header("Authorization", "Bearer " + managerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"QUEUE-B-" + suffix + "\",\"productId\":" + productId
                        + ",\"productionDate\":\"" + LocalDate.now(ZoneOffset.UTC) + "\",\"quantityProduced\":50}"));
        long batchId = batch.get("id").asLong();

        // inspection A: image attached but AI processing FAILED (no model results)
        long failedInspectionId = createSubmittedInspection(inspectorToken, batchId,
                "ITEM-QUEUE-FAIL-" + suffix, "queue-fail-" + suffix, true);
        // inspection B: healthy evidence, no processing problems
        long healthyInspectionId = createSubmittedInspection(inspectorToken, batchId,
                "ITEM-QUEUE-OK-" + suffix, "queue-ok-" + suffix, false);

        MvcResult result = mockMvc.perform(get("/api/v1/review-queue")
                        .param("limit", "50")
                        .header("Authorization", "Bearer " + inspectorToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode queue = objectMapper.readTree(result.getResponse().getContentAsString());

        Integer failedScore = null;
        Integer healthyScore = null;
        JsonNode failedItem = null;
        JsonNode healthyItem = null;
        int failedIndex = -1;
        int healthyIndex = -1;
        for (int i = 0; i < queue.size(); i++) {
            JsonNode item = queue.get(i);
            if (item.get("inspectionId").asLong() == failedInspectionId) {
                failedScore = item.get("priorityScore").asInt();
                failedItem = item;
                failedIndex = i;
            }
            if (item.get("inspectionId").asLong() == healthyInspectionId) {
                healthyScore = item.get("priorityScore").asInt();
                healthyItem = item;
                healthyIndex = i;
            }
        }

        assertNotNull(failedScore, "Failed-processing inspection must appear in the review queue");
        assertNotNull(healthyScore, "Healthy inspection must appear in the review queue");
        assertTrue(failedScore > healthyScore,
                "Failed processing must outrank healthy evidence: " + failedScore + " vs " + healthyScore);
        assertTrue(failedIndex < healthyIndex,
                "Failed inspection must be ordered before the healthy one");

        boolean hasFailedReason = false;
        for (JsonNode reason : failedItem.get("reasons")) {
            if ("FAILED_PROCESSING".equals(reason.get("factor").asText())
                    && reason.get("weight").asInt() == 40) {
                hasFailedReason = true;
            }
        }
        assertTrue(hasFailedReason,
                "Queue must explain the FAILED_PROCESSING factor with weight 40: " + failedItem.get("reasons"));

        // the unreviewed real 6-class prediction (CRACK) must raise UNREVIEWED + NEGATIVE
        assertNotNull(healthyItem, "Healthy inspection with a model result must appear in the queue");
        boolean hasUnreviewed = false;
        boolean hasNegative = false;
        for (JsonNode reason : healthyItem.get("reasons")) {
            if ("UNREVIEWED_PREDICTION".equals(reason.get("factor").asText())
                    && reason.get("weight").asInt() == 25) {
                hasUnreviewed = true;
            }
            if ("NEGATIVE_PREDICTION".equals(reason.get("factor").asText())
                    && reason.get("weight").asInt() == 10
                    && reason.get("detail").asText().contains("CRACK")) {
                hasNegative = true;
            }
        }
        assertTrue(hasUnreviewed,
                "Queue must explain the UNREVIEWED_PREDICTION factor with weight 25: " + healthyItem.get("reasons"));
        assertTrue(hasNegative,
                "A non-OK model class must raise NEGATIVE_PREDICTION with the class named: "
                        + healthyItem.get("reasons"));
        assertEquals(35, healthyScore,
                "Healthy item with unreviewed CRACK prediction should score 25+10=35 but was " + healthyScore);

        // scores must be non-increasing (the queue is sorted by priority)
        for (int i = 1; i < queue.size(); i++) {
            assertTrue(queue.get(i - 1).get("priorityScore").asInt()
                            >= queue.get(i).get("priorityScore").asInt(),
                    "Review queue must be sorted by descending priority score");
        }
    }

    // ---- helpers ----

    private long createSubmittedInspection(String inspectorToken, long batchId, String itemRef,
                                           String storageKeySuffix, boolean withFailedAttempt) throws Exception {
        JsonNode inspection = json(post("/api/v1/inspections")
                .header("Authorization", "Bearer " + inspectorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"batchId\":" + batchId + ",\"itemReference\":\"" + itemRef
                        + "\",\"evidenceScope\":\"ITEM\"}"));
        long inspectionId = inspection.get("id").asLong();

        UserEntity inspector = userRepository.findByUsername("queue_inspector").orElseThrow();
        var image = new com.qualityinspect.backend.inspection.InspectionImageEntity();
        image.setInspectionId(inspectionId);
        image.setStorageKey("test/queue-" + storageKeySuffix);
        image.setOriginalFilename("evidence.jpg");
        image.setContentType("image/jpeg");
        image.setSizeBytes(50_000L);
        image.setSha256("queue-sha-" + storageKeySuffix);
        image.setUploadedBy(inspector.getId());
        image = imageRepository.save(image);

        if (withFailedAttempt) {
            var attempt = new com.qualityinspect.backend.ml.ProcessingAttemptEntity();
            attempt.setImageId(image.getId());
            attempt.setAttemptNo(1);
            attempt.setStatus("FAILED");
            attempt.setErrorDetails("ML service unreachable during processing");
            attempt.setRequestedBy(inspector.getId());
            attemptRepository.save(attempt);
        } else {
            // healthy image: completed with a real 6-class prediction awaiting review
            var result = new com.qualityinspect.backend.ml.ModelResultEntity();
            result.setImageId(image.getId());
            result.setPredictedClass("CRACK");
            result.setScore(new java.math.BigDecimal("0.8123"));
            result.setScoreType("probability");
            result.setModelName("defect-classifier");
            result.setModelVersion("v0.1.0");
            result.setDurationMs(120);
            result.setCurrent(true);
            modelResultRepository.save(result);
        }

        mockMvc.perform(post("/api/v1/inspections/" + inspectionId + "/submit")
                        .header("Authorization", "Bearer " + inspectorToken))
                .andExpect(status().isOk());
        return inspectionId;
    }

    private UserEntity upsert(String username, String password, RoleEntity role) {
        return userRepository.findByUsername(username).map(u -> {
            u.setPasswordHash(passwordEncoder.encode(password));
            u.setEnabled(true);
            u.getRoles().clear();
            u.getRoles().add(role);
            return userRepository.save(u);
        }).orElseGet(() -> {
            UserEntity u = new UserEntity();
            u.setOrgId("default");
            u.setUsername(username);
            u.setPasswordHash(passwordEncoder.encode(password));
            u.setDisplayName(username);
            u.setEnabled(true);
            u.getRoles().add(role);
            return userRepository.save(u);
        });
    }

    private String login(String username, String password) {
        try {
            MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                    .andExpect(status().isOk())
                    .andReturn();
            return objectMapper.readTree(result.getResponse().getContentAsString())
                    .get("accessToken").asText();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private JsonNode json(MvcResult result) {
        try {
            return objectMapper.readTree(result.getResponse().getContentAsString());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private JsonNode json(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder builder) {
        try {
            return json(mockMvc.perform(builder)
                    .andExpect(status().is2xxSuccessful())
                    .andReturn());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
