package com.qualityinspect.backend.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qualityinspect.backend.ai.AiDtos.*;
import com.qualityinspect.backend.ai.AiEvidenceService.Evidence;
import com.qualityinspect.backend.common.audit.AuditService;
import com.qualityinspect.backend.common.error.ApiException.*;
import com.qualityinspect.backend.config.GenAiProperties;
import com.qualityinspect.backend.security.AuthPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

/**
 * Evidence-grounded Quality Report Assistant.
 *
 * Pipeline: authenticate -> authorize scope -> authorized SQL queries -> computed
 * facts -> (LLM if configured, otherwise a clearly labelled deterministic summary)
 * -> citation validation -> persist report + evidence snapshot.
 *
 * The model can never release batches, close defects or certify product quality —
 * it only produces text and suggestions that remain advisory.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiReportService {

    private static final String SYSTEM_PROMPT = """
            You are the QualityInspect AI report assistant for a manufacturing quality team.
            Answer ONLY using the EVIDENCE provided in the user message. Hard rules:
            1. Never invent identifiers, counts, dates, measurements or outcomes.
               Cite only real record IDs that appear in the evidence (e.g. DEF-00012, CA-00007, inspection ids).
            2. Keep CONFIRMED facts (human-confirmed defects, recorded batch decisions, completed actions)
               strictly separate from UNVALIDATED model predictions (model_results are predictions only).
            3. If evidence is missing, say exactly what is missing. If a batch or record is unknown, say so.
            4. If the user's premise conflicts with the evidence, state the conflict and present the evidence.
            5. Suggestions must be clearly marked as suggestions, never as completed work or certifications.
            6. Respond with STRICT JSON only, no prose outside JSON:
               {"responseText": "...", "suggestions": ["..."], "missingEvidence": ["..."]}
            """;

    private final AiEvidenceService evidenceService;
    private final GenAiClient genAiClient;
    private final AiReportRepository reportRepository;
    private final ObjectMapper objectMapper;
    private final AuditService auditService;
    private final GenAiProperties genAiProperties;

    // ---------------------------------------------------------------- endpoints

    @Transactional
    public AiReportResponse batchSummary(long batchId, AuthPrincipal principal) {
        Evidence evidence = evidenceService.batchEvidence(batchId);
        String requestText = "Summarize batch " + evidence.scope().get("batchCode")
                + " (quality status, defects, actions, decisions).";
        return generate("BATCH_SUMMARY", evidence, requestText, principal);
    }

    @Transactional
    public AiReportResponse qualityReport(LocalDate from, LocalDate to, Long batchId, AuthPrincipal principal) {
        Evidence evidence = batchId != null
                ? evidenceService.batchEvidence(batchId)
                : evidenceService.periodEvidence(from, to);
        String requestText = "Generate a quality report"
                + (batchId != null ? " for batch " + evidence.scope().get("batchCode") : "")
                + (from != null || to != null
                        ? " for period " + (from == null ? "…" : from) + " to " + (to == null ? "…" : to)
                        : " for the default period");
        return generate("QUALITY_REPORT", evidence, requestText, principal);
    }

    @Transactional
    public AiReportResponse question(QuestionRequest request, AuthPrincipal principal) {
        Evidence evidence;
        if (request.batchId() != null) {
            evidence = evidenceService.batchEvidence(request.batchId());
        } else {
            String code = extractBatchCode(request.question());
            if (code != null) {
                // unknown batch -> clear NotFoundException("Unknown batch: ...") from evidence service
                evidence = evidenceService.batchEvidenceByCode(code);
            } else {
                evidence = evidenceService.attentionEvidence();
            }
        }
        return generate("QUESTION", evidence, request.question().trim(), principal);
    }

    @Transactional(readOnly = true)
    public List<AiReportResponse> history(AuthPrincipal principal) {
        return reportRepository.findTop20ByCreatedByOrderByIdDesc(principal.getId()).stream()
                .map(this::toResponse).toList();
    }

    // ---------------------------------------------------------------- generation

    private AiReportResponse generate(String type, Evidence evidence, String requestText, AuthPrincipal principal) {
        List<String> facts = evidenceService.facts(evidence);

        if (genAiClient.enabled()) {
            try {
                String userPrompt = "REQUEST: " + requestText + "\n\nEVIDENCE JSON:\n"
                        + toJson(Map.of(
                                "scope", evidence.scope(),
                                "metrics", evidence.metrics(),
                                "defects", evidence.defects(),
                                "correctiveActions", evidence.actions(),
                                "batchDecisions", evidence.decisions(),
                                "historicalContext", evidence.historicalContext(),
                                "serverComputedFacts", facts));
                GenAiClient.LlmAnswer answer = genAiClient.chat(SYSTEM_PROMPT, userPrompt);
                Sanitized sanitized = sanitize(answer, evidence);
                AiReportEntity entity = persist(type, "llm", true, requestText,
                        sanitized.responseText(), facts, sanitized.suggestions(),
                        sanitized.missingEvidence(), evidence, principal,
                        genAiProperties.getProvider(), genAiProperties.getModel(), null);
                return toResponse(entity);
            } catch (ServiceUnavailableException ex) {
                persistFailure(type, requestText, evidence, principal, ex.getMessage());
                throw new ServiceUnavailableException("Report generation failed; retry is available");
            }
        }

        String devText = buildDeterministicSummary(type, requestText, facts);
        AiReportEntity entity = persist(type, "dev-no-llm", false, requestText,
                devText, facts, List.of(), List.of(), evidence, principal, null, null, null);
        return toResponse(entity);
    }

    private String buildDeterministicSummary(String type, String requestText, List<String> facts) {
        StringBuilder sb = new StringBuilder();
        sb.append("[DEVELOPMENT MODE - no LLM configured]\n");
        sb.append("This is a deterministic evidence summary computed from authorized database records. ")
          .append("It is NOT a generative-AI answer. Configure GENAI_PROVIDER and GENAI_API_KEY on the ")
          .append("backend to enable the LLM report assistant.\n\n");
        sb.append("Request: ").append(requestText).append("\n\nConfirmed facts (source: database records):\n");
        for (int i = 0; i < facts.size(); i++) {
            sb.append(i + 1).append(". ").append(facts.get(i)).append('\n');
        }
        return sb.toString();
    }

    // ---------------------------------------------------------------- grounding safety

    private record Sanitized(String responseText, List<String> suggestions, List<String> missingEvidence) {}

    /**
     * Blocks the classic LLM failure of inventing identifiers: any cited DEF-/CA- code
     * or inspection id that is not present in the authorized evidence is redacted and
     * reported in missingEvidence.
     */
    private Sanitized sanitize(GenAiClient.LlmAnswer answer, Evidence evidence) {
        List<String> missing = new ArrayList<>(answer.missingEvidence());
        String text = redactUnknownIds(answer.responseText(), evidence, missing);
        List<String> suggestions = answer.suggestions().stream()
                .map(s -> redactUnknownIds(s, evidence, missing))
                .toList();
        return new Sanitized(text, suggestions, missing.stream().distinct().toList());
    }

    private String redactUnknownIds(String input, Evidence evidence, List<String> missing) {
        if (input == null || input.isBlank()) {
            return "";
        }
        String result = input;
        java.util.regex.Matcher codeMatcher =
                java.util.regex.Pattern.compile("\\b(?:DEF|CA)-\\d{4,6}\\b").matcher(result);
        StringBuilder rebuilt = new StringBuilder();
        while (codeMatcher.find()) {
            String code = codeMatcher.group();
            boolean known = evidence.knownDefectCodes().contains(code)
                    || evidence.knownActionCodes().contains(code);
            if (!known) {
                missing.add("Model cited unknown record '" + code + "' which is not in the authorized evidence");
            }
        }
        result = java.util.regex.Pattern.compile("\\b(DEF|CA)-\\d{4,6}\\b").matcher(result).replaceAll(match -> {
            String code = match.group();
            boolean known = evidence.knownDefectCodes().contains(code)
                    || evidence.knownActionCodes().contains(code);
            return known ? code : "[unverified-id]";
        });
        result = java.util.regex.Pattern.compile("(?i)inspection\\s*#?\\s*(\\d{1,10})").matcher(result).replaceAll(match -> {
            String raw = match.group(1);
            try {
                long id = Long.parseLong(raw);
                if (evidence.knownInspectionIds().contains(id)) {
                    return match.group();
                }
                missing.add("Model cited unknown inspection " + id + " which is not in the authorized evidence");
                return "inspection [unverified-id]";
            } catch (NumberFormatException e) {
                return match.group();
            }
        });
        return result;
    }

    // ---------------------------------------------------------------- persistence

    private AiReportEntity persist(String type, String mode, boolean generated, String requestText,
                                   String responseText, List<String> facts, List<String> suggestions,
                                   List<String> missingEvidence, Evidence evidence, AuthPrincipal principal,
                                   String provider, String model, String errorDetails) {
        AiReportEntity entity = new AiReportEntity();
        entity.setReportType(type);
        entity.setMode(mode);
        entity.setStatus("COMPLETED");
        entity.setRequestText(truncate(requestText, 4000));
        entity.setResponseText(responseText);
        entity.setFacts(toJson(facts));
        entity.setSuggestions(toJson(suggestions));
        entity.setCreatedBy(principal.getId());
        entity.setModelProvider(provider);
        entity.setModelName(model);

        Map<String, Object> grounding = new LinkedHashMap<>();
        grounding.put("scope", evidence.scope());
        grounding.put("metrics", evidence.metrics());
        grounding.put("sourceIds", Map.of(
                "batchIds", evidence.scope().get("batchId") == null ? List.of()
                        : List.of(Long.parseLong(String.valueOf(evidence.scope().get("batchId")))),
                "inspectionIds", evidence.knownInspectionIds(),
                "defectCodes", evidence.knownDefectCodes(),
                "actionCodes", evidence.knownActionCodes(),
                "batchCodes", evidence.knownBatchCodes()));
        grounding.put("missingEvidence", missingEvidence);
        grounding.put("generated", generated);
        entity.setGroundingJson(toJson(grounding));
        entity.setScope(toJson(evidence.scope()));

        entity = reportRepository.save(entity);
        auditService.record("AI_REPORT", entity.getId(), "AI_REPORT_GENERATED", null,
                Map.of("type", type, "mode", mode));
        return entity;
    }

    private void persistFailure(String type, String requestText, Evidence evidence, AuthPrincipal principal,
                                String error) {
        try {
            AiReportEntity entity = new AiReportEntity();
            entity.setReportType(type);
            entity.setMode("llm");
            entity.setStatus("FAILED");
            entity.setRequestText(truncate(requestText, 4000));
            entity.setErrorDetails(truncate(error, 2000));
            entity.setCreatedBy(principal.getId());
            entity.setGroundingJson(toJson(Map.of("scope", evidence.scope(), "metrics", evidence.metrics())));
            entity.setScope(toJson(evidence.scope()));
            reportRepository.save(entity);
            auditService.record("AI_REPORT", entity.getId(), "AI_REPORT_FAILED", error, null);
        } catch (Exception ex) {
            log.warn("Could not persist failed AI report: {}", ex.getMessage());
        }
    }

    private AiReportResponse toResponse(AiReportEntity entity) {
        List<String> facts = parseList(entity.getFacts());
        List<String> suggestions = parseList(entity.getSuggestions());
        Map<String, Object> grounding = parseMap(entity.getGroundingJson());
        List<String> missing = new ArrayList<>();
        Object missingRaw = grounding.get("missingEvidence");
        if (missingRaw instanceof List<?> list) {
            list.forEach(item -> missing.add(String.valueOf(item)));
        }
        SourceIds sourceIds = extractSourceIds(grounding);
        boolean generated = entity.getModelProvider() != null && "llm".equals(entity.getMode());
        return new AiReportResponse(
                entity.getId(), entity.getReportType(), entity.getMode(), generated,
                entity.getResponseText(), facts, suggestions, missing, sourceIds, entity.getCreatedAt());
    }

    @SuppressWarnings("unchecked")
    private SourceIds extractSourceIds(Map<String, Object> grounding) {
        Object raw = grounding.get("sourceIds");
        if (!(raw instanceof Map)) {
            return new SourceIds(List.of(), List.of(), List.of(), List.of());
        }
        Map<String, Object> ids = (Map<String, Object>) raw;
        return new SourceIds(
                toLongList(ids.get("batchIds")),
                toLongList(ids.get("inspectionIds")),
                toStringList(ids.get("defectCodes")),
                toStringList(ids.get("actionCodes")));
    }

    private List<Long> toLongList(Object value) {
        List<Long> list = new ArrayList<>();
        if (value instanceof Collection<?> items) {
            for (Object item : items) {
                try {
                    list.add(Long.parseLong(String.valueOf(item)));
                } catch (NumberFormatException ignored) {
                    // skip non-numeric
                }
            }
        }
        return list;
    }

    private List<String> toStringList(Object value) {
        List<String> list = new ArrayList<>();
        if (value instanceof Collection<?> items) {
            items.forEach(item -> list.add(String.valueOf(item)));
        }
        return list;
    }

    private List<String> parseList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "{}";
        }
    }

    /**
     * Detects an explicit batch reference in a free-text question.
     * The token must look like a real batch code (>=3 chars, contains a digit)
     * so ordinary prose such as "batch as released" is not misread as a code.
     */
    static String extractBatchCode(String question) {
        if (question == null) {
            return null;
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("(?i)\\bbatch\\s*(?:code\\s*)?[:#-]?\\s*((?=[A-Za-z0-9._-]*\\d)[A-Za-z0-9][A-Za-z0-9._-]{2,39})\\b")
                .matcher(question);
        return matcher.find() ? matcher.group(1) : null;
    }

    private String truncate(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }
}
