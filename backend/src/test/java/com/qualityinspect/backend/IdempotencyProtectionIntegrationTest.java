package com.qualityinspect.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qualityinspect.backend.user.RoleEntity;
import com.qualityinspect.backend.user.RoleRepository;
import com.qualityinspect.backend.user.UserEntity;
import com.qualityinspect.backend.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Duplicate-submission protection (Idempotency-Key) for defect, corrective-action
 * and batch-decision creation, plus the defect list date-range filter.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class IdempotencyProtectionIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired RoleRepository roleRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired com.qualityinspect.backend.inspection.InspectionImageRepository imageRepository;

    String inspectorToken;
    String managerToken;
    long batchId;
    long inspectionId;
    long defectId;
    long ownerId;

    @BeforeEach
    void setUpFlow() throws Exception {
        RoleEntity inspectorRole = roleRepository.findByCode("INSPECTOR").orElseThrow();
        RoleEntity managerRole = roleRepository.findByCode("QUALITY_MANAGER").orElseThrow();

        UserEntity inspector = upsert("idem_inspector", "Inspector!123", inspectorRole);
        UserEntity manager = upsert("idem_manager", "Manager!12345", managerRole);
        ownerId = manager.getId();

        inspectorToken = login("idem_inspector", "Inspector!123");
        managerToken = login("idem_manager", "Manager!12345");

        long suffix = System.nanoTime() % 1_000_000_000L;

        // product + batch (quality manager only)
        String productCode = "IDEM-P-" + suffix;
        JsonNode product = json(post("/api/v1/products")
                .header("Authorization", "Bearer " + managerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"" + productCode + "\",\"name\":\"Idem Widget\",\"category\":\"PARTS\"}"));
        long productId = product.get("id").asLong();

        JsonNode batch = json(post("/api/v1/batches")
                .header("Authorization", "Bearer " + managerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"IDEM-B-" + suffix + "\",\"productId\":" + productId
                        + ",\"productionDate\":\"" + LocalDate.now(ZoneOffset.UTC) + "\",\"quantityProduced\":100}"));
        batchId = batch.get("id").asLong();

        // draft inspection (inspector)
        JsonNode inspection = json(post("/api/v1/inspections")
                .header("Authorization", "Bearer " + inspectorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"batchId\":" + batchId
                        + ",\"itemReference\":\"ITEM-IDEM-1\",\"evidenceScope\":\"ITEM\"}"));
        inspectionId = inspection.get("id").asLong();

        // seed one evidence image row directly (no file needed; submit only counts rows)
        var image = new com.qualityinspect.backend.inspection.InspectionImageEntity();
        image.setInspectionId(inspectionId);
        image.setStorageKey("test/idem-" + suffix);
        image.setOriginalFilename("evidence.jpg");
        image.setContentType("image/jpeg");
        image.setSizeBytes(50_000L);
        image.setSha256("idem-sha-" + suffix);
        image.setUploadedBy(inspector.getId());
        image = imageRepository.save(image);

        // submit + human confirm a defect outcome
        mockMvc.perform(post("/api/v1/inspections/" + inspectionId + "/submit")
                        .header("Authorization", "Bearer " + inspectorToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/inspections/" + inspectionId + "/reviews")
                        .header("Authorization", "Bearer " + inspectorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"CONFIRM\",\"confirmedOutcome\":\"POTENTIALLY_DEFECTIVE\","
                                + "\"reason\":\"Visible scratch on the housing surface\"}"))
                .andExpect(status().isCreated());

        // create the defect once (no idempotency key) so later tests can act on it
        JsonNode defect = json(post("/api/v1/defects")
                .header("Authorization", "Bearer " + inspectorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"inspectionId\":" + inspectionId
                        + ",\"description\":\"Scratch confirmed on housing edge\",\"severity\":\"MEDIUM\"}"));
        defectId = defect.get("id").asLong();
    }

    // ---- defect creation replays the first response for a repeated key ----
    @Test
    void defectCreate_sameKey_replaysSameDefect() throws Exception {
        String body = "{\"inspectionId\":" + inspectionId
                + ",\"description\":\"Duplicate submission test defect\",\"severity\":\"HIGH\"}";

        // NOTE: the inspection already has a defect (setUp), so a fresh submission
        // would 409 — exercise replay via a second identical keyed request instead:
        // create with key on a second inspection.
        long suffix = System.nanoTime() % 1_000_000_000L;
        JsonNode inspection2 = json(post("/api/v1/inspections")
                .header("Authorization", "Bearer " + inspectorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"batchId\":" + batchId
                        + ",\"itemReference\":\"ITEM-IDEM-2\",\"evidenceScope\":\"ITEM\"}"));
        long inspection2Id = inspection2.get("id").asLong();

        var image = new com.qualityinspect.backend.inspection.InspectionImageEntity();
        image.setInspectionId(inspection2Id);
        image.setStorageKey("test/idem2-" + suffix);
        image.setOriginalFilename("evidence.jpg");
        image.setContentType("image/jpeg");
        image.setSizeBytes(50_000L);
        image.setSha256("idem2-sha-" + suffix);
        image.setUploadedBy(userRepository.findByUsername("idem_inspector").orElseThrow().getId());
        imageRepository.save(image);

        mockMvc.perform(post("/api/v1/inspections/" + inspection2Id + "/submit")
                        .header("Authorization", "Bearer " + inspectorToken))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/inspections/" + inspection2Id + "/reviews")
                        .header("Authorization", "Bearer " + inspectorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"CONFIRM\",\"confirmedOutcome\":\"POTENTIALLY_DEFECTIVE\","
                                + "\"reason\":\"Repeat defect for idempotency check\"}"))
                .andExpect(status().isCreated());

        String body2 = "{\"inspectionId\":" + inspection2Id
                + ",\"description\":\"Duplicate submission test defect\",\"severity\":\"HIGH\"}";

        MvcResult first = mockMvc.perform(post("/api/v1/defects")
                        .header("Authorization", "Bearer " + inspectorToken)
                        .header("Idempotency-Key", "defect-key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body2))
                .andExpect(status().isCreated())
                .andReturn();
        long firstId = objectMapper.readTree(first.getResponse().getContentAsString()).get("id").asLong();

        MvcResult second = mockMvc.perform(post("/api/v1/defects")
                        .header("Authorization", "Bearer " + inspectorToken)
                        .header("Idempotency-Key", "defect-key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body2))
                .andExpect(status().isCreated())
                .andReturn();
        long secondId = objectMapper.readTree(second.getResponse().getContentAsString()).get("id").asLong();

        assertEquals(firstId, secondId, "Repeated Idempotency-Key must replay the first defect");
    }

    @Test
    void defectCreate_sameKeyDifferentPayload_returns409() throws Exception {
        long suffix = System.nanoTime() % 1_000_000_000L;
        JsonNode inspection2 = json(post("/api/v1/inspections")
                .header("Authorization", "Bearer " + inspectorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"batchId\":" + batchId
                        + ",\"itemReference\":\"ITEM-IDEM-3\",\"evidenceScope\":\"ITEM\"}"));
        long inspection2Id = inspection2.get("id").asLong();

        var image = new com.qualityinspect.backend.inspection.InspectionImageEntity();
        image.setInspectionId(inspection2Id);
        image.setStorageKey("test/idem3-" + suffix);
        image.setOriginalFilename("evidence.jpg");
        image.setContentType("image/jpeg");
        image.setSizeBytes(50_000L);
        image.setSha256("idem3-sha-" + suffix);
        image.setUploadedBy(userRepository.findByUsername("idem_inspector").orElseThrow().getId());
        imageRepository.save(image);

        mockMvc.perform(post("/api/v1/inspections/" + inspection2Id + "/submit")
                        .header("Authorization", "Bearer " + inspectorToken))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/inspections/" + inspection2Id + "/reviews")
                        .header("Authorization", "Bearer " + inspectorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"CONFIRM\",\"confirmedOutcome\":\"POTENTIALLY_DEFECTIVE\","
                                + "\"reason\":\"Repeat defect for conflict check\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/defects")
                        .header("Authorization", "Bearer " + inspectorToken)
                        .header("Idempotency-Key", "defect-key-conflict")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inspectionId\":" + inspection2Id
                                + ",\"description\":\"First description here\",\"severity\":\"HIGH\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/defects")
                        .header("Authorization", "Bearer " + inspectorToken)
                        .header("Idempotency-Key", "defect-key-conflict")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inspectionId\":" + inspection2Id
                                + ",\"description\":\"Different description now\",\"severity\":\"LOW\"}"))
                .andExpect(status().isConflict());
    }

    // ---- corrective action idempotency ----
    @Test
    void actionCreate_sameKey_replaysSameAction() throws Exception {
        String body = "{\"defectId\":" + defectId
                + ",\"title\":\"Rework the scratched housing\",\"ownerId\":" + ownerId
                + ",\"targetDate\":\"" + LocalDate.now(ZoneOffset.UTC).plusDays(7) + "\"}";

        MvcResult first = mockMvc.perform(post("/api/v1/actions")
                        .header("Authorization", "Bearer " + managerToken)
                        .header("Idempotency-Key", "action-key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        long firstId = objectMapper.readTree(first.getResponse().getContentAsString()).get("id").asLong();

        MvcResult second = mockMvc.perform(post("/api/v1/actions")
                        .header("Authorization", "Bearer " + managerToken)
                        .header("Idempotency-Key", "action-key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        long secondId = objectMapper.readTree(second.getResponse().getContentAsString()).get("id").asLong();

        assertEquals(firstId, secondId, "Repeated Idempotency-Key must replay the first action");
    }

    // ---- batch decision idempotency ----
    @Test
    void decisionCreate_sameKey_replaysSameDecision() throws Exception {
        String body = "{\"decision\":\"HOLD\",\"reason\":\"Quality hold pending rework verification\"}";

        MvcResult first = mockMvc.perform(post("/api/v1/batches/" + batchId + "/decisions")
                        .header("Authorization", "Bearer " + managerToken)
                        .header("Idempotency-Key", "decision-key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        long firstId = objectMapper.readTree(first.getResponse().getContentAsString()).get("id").asLong();

        MvcResult second = mockMvc.perform(post("/api/v1/batches/" + batchId + "/decisions")
                        .header("Authorization", "Bearer " + managerToken)
                        .header("Idempotency-Key", "decision-key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        long secondId = objectMapper.readTree(second.getResponse().getContentAsString()).get("id").asLong();

        assertEquals(firstId, secondId, "Repeated Idempotency-Key must replay the first decision");
    }

    @Test
    void decisionCreate_sameKeyDifferentPayload_returns409() throws Exception {
        mockMvc.perform(post("/api/v1/batches/" + batchId + "/decisions")
                        .header("Authorization", "Bearer " + managerToken)
                        .header("Idempotency-Key", "decision-key-conflict")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"HOLD\",\"reason\":\"First hold reason text\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/batches/" + batchId + "/decisions")
                        .header("Authorization", "Bearer " + managerToken)
                        .header("Idempotency-Key", "decision-key-conflict")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"RELEASE\",\"reason\":\"Changed my mind entirely\"}"))
                .andExpect(status().isConflict());
    }

    // ---- defect list date-range filter ----
    @Test
    void defectList_dateRange_filtersByCreatedAt() throws Exception {
        String today = LocalDate.now(ZoneOffset.UTC).toString();

        MvcResult inRange = mockMvc.perform(get("/api/v1/defects")
                        .param("from", today).param("to", today)
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode content = objectMapper.readTree(inRange.getResponse().getContentAsString()).get("content");
        boolean found = false;
        for (JsonNode node : content) {
            if (node.get("id").asLong() == defectId) {
                found = true;
            }
        }
        assertTrue(found, "Defect created today must appear in today's date range");

        mockMvc.perform(get("/api/v1/defects")
                        .param("from", "2099-01-01").param("to", "2099-01-02")
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());
    }

    // ---- helpers ----

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
