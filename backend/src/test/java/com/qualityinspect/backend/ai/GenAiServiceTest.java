package com.qualityinspect.backend.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qualityinspect.backend.ai.AiEvidenceService.Evidence;
import com.qualityinspect.backend.common.audit.AuditService;
import com.qualityinspect.backend.common.error.ApiException;
import com.qualityinspect.backend.config.GenAiProperties;
import com.qualityinspect.backend.security.AuthPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GenAiServiceTest {

    @Mock AiEvidenceService evidenceService;
    @Mock GenAiClient genAiClient;
    @Mock AiReportRepository reportRepository;
    @Mock AuditService auditService;

    ObjectMapper mapper = new ObjectMapper();
    GenAiProperties props;
    AiReportService service;
    AuthPrincipal principal;

    @BeforeEach
    void setUp() {
        props = new GenAiProperties(); // provider = "none" by default
        service = new AiReportService(evidenceService, genAiClient, reportRepository,
                mapper, auditService, props);
        principal = new AuthPrincipal(1L, "qmadmin", "hash", true, Set.of("QUALITY_MANAGER"));

        when(genAiClient.enabled()).thenReturn(false);
        when(evidenceService.facts(any())).thenReturn(List.of("Batch has 5 confirmed defects."));
        when(reportRepository.save(any(AiReportEntity.class))).thenAnswer(inv -> {
            AiReportEntity e = inv.getArgument(0);
            e.setId(7L);
            return e;
        });
    }

    private Evidence sampleEvidence() {
        return new Evidence(
                Map.of("batchId", 1L, "batchCode", "BATCH-2026-001", "productCode", "SKU-A"),
                Map.of("distinctItemsInspected", 10, "distinctItemsWithConfirmedDefects", 1),
                List.of(Map.of("code", "DEF-00001", "severity", "MEDIUM", "status", "OPEN")),
                List.of(), List.of(), List.of(),
                Set.of("DEF-00001"),
                Set.of(),
                Set.of(1L),
                Set.of("BATCH-2026-001"));
    }

    // ---- Test 1: provider=none returns labelled dev summary, generated=false ----
    @Test
    void batchSummary_devNoLlm_returnsLabelledDeterministicResponse() {
        when(evidenceService.batchEvidence(1L)).thenReturn(sampleEvidence());

        var resp = service.batchSummary(1L, principal);

        assertNotNull(resp);
        assertEquals("dev-no-llm", resp.mode());
        assertFalse(resp.generated());
        assertTrue(resp.responseText().toLowerCase().contains("development mode"),
                "Response must be labelled as dev-mode output: " + resp.responseText());
        assertTrue(resp.responseText().contains("BATCH_SUMMARY")
                        || resp.responseText().contains("Summarize batch"),
                "Response must echo the request: " + resp.responseText());
    }

    // ---- Test 2: deterministic output is stable across calls ----
    @Test
    void batchSummary_devNoLlm_isDeterministic() {
        when(evidenceService.batchEvidence(1L)).thenReturn(sampleEvidence());

        var r1 = service.batchSummary(1L, principal);
        var r2 = service.batchSummary(1L, principal);

        assertEquals(r1.responseText(), r2.responseText());
    }

    // ---- Test 3: unknown batch throws NotFoundException ----
    @Test
    void batchSummary_unknownBatch_throwsNotFound() {
        when(evidenceService.batchEvidence(999L))
                .thenThrow(new ApiException.NotFoundException("Unknown batch: 999"));

        assertThrows(ApiException.NotFoundException.class,
                () -> service.batchSummary(999L, principal));
    }

    // ---- Test 4: citation redaction strips unknown record IDs ----
    @Test
    void sanitize_redactsUnknownCitationIds() {
        GenAiClient.LlmAnswer answer = new GenAiClient.LlmAnswer(
                "Batch has defect DEF-99999 and action CA-88888 which are not real. Known DEF-00001 stands.",
                List.of("Check DEF-99999"), List.of());

        try {
            var m = AiReportService.class.getDeclaredMethod("sanitize",
                    GenAiClient.LlmAnswer.class, Evidence.class);
            m.setAccessible(true);
            Object result = m.invoke(service, answer, sampleEvidence());

            var rt = result.getClass().getDeclaredMethod("responseText");
            rt.setAccessible(true);
            String text = (String) rt.invoke(result);

            assertFalse(text.contains("DEF-99999"),
                    "Unknown ID DEF-99999 must be redacted, got: " + text);
            assertFalse(text.contains("CA-88888"),
                    "Unknown ID CA-88888 must be redacted, got: " + text);
            assertTrue(text.contains("DEF-00001"),
                    "Known ID DEF-00001 must be preserved, got: " + text);
        } catch (Exception e) {
            fail("sanitize invocation failed: " + e);
        }
    }

    // ---- Test 5: GenAiClient.parseContent handles fenced JSON, bare JSON, plain text ----
    @Test
    void parseContent_handlesAllFormats() {
        try {
            var m = GenAiClient.class.getDeclaredMethod("parseContent", String.class);
            m.setAccessible(true);
            GenAiClient client = new GenAiClient(props, mapper);

            Object fenced = m.invoke(client,
                    "```json\n{\"responseText\":\"ok\",\"suggestions\":[\"a\"],\"missingEvidence\":[]}\n```");
            var rText = GenAiClient.LlmAnswer.class.getMethod("responseText");
            assertEquals("ok", rText.invoke(fenced));

            Object bare = m.invoke(client,
                    "{\"responseText\":\"bare\",\"suggestions\":[],\"missingEvidence\":[]}");
            assertEquals("bare", rText.invoke(bare));

            Object plain = m.invoke(client, "Just a sentence.");
            assertEquals("Just a sentence.", rText.invoke(plain));
        } catch (Exception e) {
            fail("parseContent invocation failed: " + e);
        }
    }

    // ---- Test 6: supported question through the LLM path keeps known citations ----
    @Test
    void question_supportedQuestion_llm_keepsKnownCitations() {
        when(genAiClient.enabled()).thenReturn(true);
        when(evidenceService.batchEvidence(1L)).thenReturn(sampleEvidence());
        when(genAiClient.chat(any(), any())).thenReturn(new GenAiClient.LlmAnswer(
                "Defect DEF-00001 is OPEN on batch BATCH-2026-001.",
                List.of("Suggest reviewing DEF-00001"), List.of()));

        var resp = service.question(
                new com.qualityinspect.backend.ai.AiDtos.QuestionRequest("What is the status of DEF-00001?", 1L),
                principal);

        assertEquals("llm", resp.mode());
        assertTrue(resp.generated());
        assertTrue(resp.responseText().contains("DEF-00001"));
        assertTrue(resp.missingEvidence().isEmpty(),
                "Known citation must not be flagged missing: " + resp.missingEvidence());
    }

    // ---- Test 7: unknown batch referenced in a question throws NotFoundException ----
    @Test
    void question_unknownBatchCode_throwsNotFound() {
        when(evidenceService.batchEvidenceByCode("BATCH-NOPE-999"))
                .thenThrow(new ApiException.NotFoundException("Unknown batch: no batch with code 'BATCH-NOPE-999' exists"));

        assertThrows(ApiException.NotFoundException.class, () -> service.question(
                new com.qualityinspect.backend.ai.AiDtos.QuestionRequest(
                        "What is the status of batch BATCH-NOPE-999?", null),
                principal));
    }

    // ---- Test 8: misleading premise — server facts + evidence contradict the user's claim ----
    @Test
    void question_misleadingPremise_factsContradictFalseClaim() {
        Evidence emptyDefects = new Evidence(
                Map.of("batchId", 1L, "batchCode", "BATCH-2026-001", "productCode", "SKU-A"),
                Map.of("distinctItemsInspected", 10, "distinctItemsWithConfirmedDefects", 0),
                List.of(), List.of(), List.of(), List.of(),
                Set.of(), Set.of(), Set.of(), Set.of("BATCH-2026-001"));
        when(genAiClient.enabled()).thenReturn(true);
        when(evidenceService.batchEvidence(1L)).thenReturn(emptyDefects);
        when(evidenceService.facts(any())).thenReturn(
                List.of("Confirmed defective items = 0; confirmed defect rate = 0.00% (0/10)."));
        when(genAiClient.chat(any(), any())).thenReturn(
                new GenAiClient.LlmAnswer("There are 5 confirmed defects.", List.of(), List.of()));

        var resp = service.question(
                new com.qualityinspect.backend.ai.AiDtos.QuestionRequest(
                        "Summarize the 5 confirmed defects in this batch", 1L),
                principal);

        assertTrue(resp.facts().stream().anyMatch(f -> f.contains("Confirmed defective items = 0")),
                "Server-computed facts must state the true count: " + resp.facts());
        var systemCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        var userCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(genAiClient).chat(systemCaptor.capture(), userCaptor.capture());
        assertTrue(systemCaptor.getValue().contains("premise conflicts"),
                "System prompt must instruct the model to handle conflicting premises");
        assertTrue(userCaptor.getValue().contains("\"distinctItemsWithConfirmedDefects\":0"),
                "LLM must receive the contradicting evidence snapshot");
    }

    // ---- Test 9: unauthorized inspection IDs are redacted even with an empty known-ID set ----
    @Test
    void sanitize_redactsUnknownInspectionIds_evenWhenEvidenceHasNone() {
        Evidence noInspections = new Evidence(
                Map.of("scope", "attention"), Map.of(), List.of(), List.of(), List.of(), List.of(),
                Set.of(), Set.of(), Set.of(), Set.of());
        GenAiClient.LlmAnswer answer = new GenAiClient.LlmAnswer(
                "Reviewed inspection 42 and inspection 41 records.", List.of(), List.of());

        try {
            var m = AiReportService.class.getDeclaredMethod("sanitize",
                    GenAiClient.LlmAnswer.class, Evidence.class);
            m.setAccessible(true);
            Object result = m.invoke(service, answer, noInspections);
            var rt = result.getClass().getDeclaredMethod("responseText");
            rt.setAccessible(true);
            String text = (String) rt.invoke(result);

            assertFalse(text.contains("inspection 42"),
                    "Unknown inspection id must be redacted, got: " + text);
            assertFalse(text.contains("inspection 41"),
                    "Unknown inspection id must be redacted, got: " + text);
            assertTrue(text.contains("[unverified-id]"),
                    "Redaction marker expected, got: " + text);
        } catch (Exception e) {
            fail("sanitize invocation failed: " + e);
        }
    }
}
