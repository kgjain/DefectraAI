package com.qualityinspect.backend.ai;

import com.qualityinspect.backend.action.ActionEntity;
import com.qualityinspect.backend.action.ActionRepository;
import com.qualityinspect.backend.batch.BatchEntity;
import com.qualityinspect.backend.batch.BatchRepository;
import com.qualityinspect.backend.common.error.ApiException.NotFoundException;
import com.qualityinspect.backend.dashboard.MetricQueries;
import com.qualityinspect.backend.decision.DecisionDtos.BatchDecisionResponse;
import com.qualityinspect.backend.decision.DecisionService;
import com.qualityinspect.backend.defect.DefectEntity;
import com.qualityinspect.backend.defect.DefectRepository;
import com.qualityinspect.backend.inspection.InspectionEntity;
import com.qualityinspect.backend.inspection.InspectionRepository;
import com.qualityinspect.backend.product.ProductRepository;
import com.qualityinspect.backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;

/**
 * Builds the ONLY data that may be shown to the Generative AI model:
 * counts computed in SQL, plus authorized record extracts with their real IDs.
 * Everything else stays on the server.
 */
@Service
@RequiredArgsConstructor
public class AiEvidenceService {

    private final BatchRepository batchRepository;
    private final ProductRepository productRepository;
    private final InspectionRepository inspectionRepository;
    private final DefectRepository defectRepository;
    private final ActionRepository actionRepository;
    private final MetricQueries metrics;
    private final DecisionService decisionService;
    private final UserRepository userRepository;

    public record Evidence(
            Map<String, Object> scope,
            Map<String, Object> metrics,
            List<Map<String, Object>> defects,
            List<Map<String, Object>> actions,
            List<Map<String, Object>> decisions,
            List<Map<String, Object>> historicalContext,
            Set<String> knownDefectCodes,
            Set<String> knownActionCodes,
            Set<Long> knownInspectionIds,
            Set<String> knownBatchCodes) {}

    // ---------------------------------------------------------------- batch

    @Transactional(readOnly = true)
    public Evidence batchEvidence(Long batchId) {
        BatchEntity batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new NotFoundException("Unknown batch: no batch with id " + batchId + " exists"));
        return batchEvidence(batch);
    }

    @Transactional(readOnly = true)
    public Evidence batchEvidenceByCode(String batchCode) {
        BatchEntity batch = batchRepository.findByCode(batchCode)
                .orElseThrow(() -> new NotFoundException("Unknown batch: no batch with code '" + batchCode + "' exists"));
        return batchEvidence(batch);
    }

    private Evidence batchEvidence(BatchEntity batch) {
        long inspected = n(metrics.countDistinctItemsInspected(batch.getId()));
        long reviewed = n(metrics.countDistinctItemsReviewed(batch.getId()));
        long pendingReviews = n(metrics.countPendingReviews(batch.getId()));
        long unreviewedPredictions = n(metrics.countUnreviewedPredictions(batch.getId()));
        long confirmedDefectItems = n(metrics.countDistinctDefectiveItems(batch.getId()));
        long confirmedDefectRecords = n(metrics.countConfirmedDefects(batch.getId()));
        long openActions = n(metrics.countOpenActions(batch.getId()));
        long overdueActions = n(metrics.countOverdueActions(batch.getId()));
        long pendingProcessing = n(metrics.countAwaitingOrFailedProcessing(batch.getId()));
        String productName = productRepository.findById(batch.getProductId()).map(p -> p.getName()).orElse("?");

        Map<String, Long> predictions = new LinkedHashMap<>();
        for (Object[] row : metrics.countPredictionsByClass(batch.getId())) {
            predictions.put(String.valueOf(row[0]), ((Number) row[1]).longValue());
        }

        Map<String, Object> scope = new LinkedHashMap<>();
        scope.put("batchId", batch.getId());
        scope.put("batchCode", batch.getCode());
        scope.put("productId", batch.getProductId());
        scope.put("productName", productName);
        scope.put("productionDate", batch.getProductionDate() == null ? null : batch.getProductionDate().toString());
        scope.put("quantityProduced", batch.getQuantityProduced());
        scope.put("batchStatus", batch.getStatus());

        Map<String, Object> metricsMap = new LinkedHashMap<>();
        metricsMap.put("distinctItemsInspected", inspected);
        metricsMap.put("distinctItemsReviewed", reviewed);
        metricsMap.put("pendingReviews", pendingReviews);
        metricsMap.put("unreviewedModelPredictions", unreviewedPredictions);
        metricsMap.put("pendingOrFailedProcessing", pendingProcessing);
        metricsMap.put("distinctItemsWithConfirmedDefects", confirmedDefectItems);
        metricsMap.put("confirmedDefectRecords", confirmedDefectRecords);
        metricsMap.put("confirmedDefectRate", inspected > 0
                ? Math.round(10000.0 * confirmedDefectItems / inspected) / 10000.0 : null);
        metricsMap.put("rateNumerator", confirmedDefectItems);
        metricsMap.put("rateDenominator", inspected);
        metricsMap.put("openCorrectiveActions", openActions);
        metricsMap.put("overdueCorrectiveActions", overdueActions);
        metricsMap.put("currentModelPredictionsByClass", predictions);

        List<Map<String, Object>> defects = new ArrayList<>();
        java.time.Instant fromAll = java.time.Instant.EPOCH;
        java.time.Instant toAll = java.time.Instant.parse("2999-12-31T23:59:59Z");
        for (DefectEntity d : defectRepository.search(null, null, batch.getId(), null, null, null, null,
                fromAll, toAll, PageRequest.of(0, 25)).getContent()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("code", d.getCode());
            m.put("severity", d.getSeverity());
            m.put("status", d.getStatus());
            m.put("category", d.getCategory());
            m.put("itemKey", d.getItemKey());
            m.put("description", truncate(d.getDescription(), 200));
            m.put("createdAt", d.getCreatedAt() == null ? null : d.getCreatedAt().toString());
            defects.add(m);
        }

        List<Map<String, Object>> actions = new ArrayList<>();
        Set<String> actionCodes = new LinkedHashSet<>();
        for (ActionEntity a : actionRepository.findByBatchId(batch.getId())) {
            if (actions.size() >= 25) break;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("code", a.getCode());
            m.put("title", a.getTitle());
            m.put("status", a.getStatus());
            m.put("owner", userRepository.findById(a.getOwnerId()).map(u -> u.getUsername()).orElse("?"));
            m.put("targetDate", a.getTargetDate() == null ? null : a.getTargetDate().toString());
            m.put("overdue", a.isOverdue());
            actions.add(m);
            actionCodes.add(a.getCode());
        }

        List<Map<String, Object>> decisions = new ArrayList<>();
        for (BatchDecisionResponse d : decisionService.history(batch.getId())) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("decision", d.decision());
            m.put("reason", truncate(d.reason(), 200));
            m.put("decidedBy", d.decidedBy());
            m.put("decidedAt", d.decidedAt() == null ? null : d.decidedAt().toString());
            decisions.add(m);
        }

        List<InspectionEntity> recentInspections = inspectionRepository
                .search(batch.getId(), null, "SUBMITTED", null, null, null, null, null, null, PageRequest.of(0, 100))
                .getContent();

        return new Evidence(scope, metricsMap, defects, actions, decisions, historicalContext(5),
                defectCodesOf(defects), actionCodes,
                recentInspections.stream().map(InspectionEntity::getId).collect(java.util.stream.Collectors.toSet()),
                Set.of(batch.getCode()));
    }

    // ---------------------------------------------------------------- global

    @Transactional(readOnly = true)
    public Evidence attentionEvidence() {
        Instant monthAgo = Instant.now().minus(30, java.time.temporal.ChronoUnit.DAYS);
        long pendingReviews = n(metrics.countPendingReviews(null));
        long pendingOrFailed = n(metrics.countAwaitingOrFailedProcessing(null));
        long openActions = n(metrics.countOpenActions(null));
        long overdueActions = n(metrics.countOverdueActions(null));
        long inspected = n(metrics.countDistinctItemsInspected(null));
        long defectiveItems = n(metrics.countDistinctDefectiveItems(null));

        Map<String, Object> scope = Map.of("scope", "all batches (attention view)");
        Map<String, Object> metricsMap = new LinkedHashMap<>();
        metricsMap.put("pendingHumanReviews", pendingReviews);
        metricsMap.put("pendingOrFailedProcessing", pendingOrFailed);
        metricsMap.put("openCorrectiveActions", openActions);
        metricsMap.put("overdueCorrectiveActions", overdueActions);
        metricsMap.put("distinctItemsInspected", inspected);
        metricsMap.put("distinctItemsWithConfirmedDefects", defectiveItems);
        metricsMap.put("confirmedDefectRate", inspected > 0
                ? Math.round(10000.0 * defectiveItems / inspected) / 10000.0 : null);

        List<Map<String, Object>> actions = new ArrayList<>();
        Set<String> actionCodes = new LinkedHashSet<>();
        for (ActionEntity a : actionRepository.findOverdue(LocalDate.now())) {
            if (actions.size() >= 15) break;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("code", a.getCode());
            m.put("title", a.getTitle());
            m.put("status", a.getStatus());
            m.put("owner", userRepository.findById(a.getOwnerId()).map(u -> u.getUsername()).orElse("?"));
            m.put("targetDate", a.getTargetDate() == null ? null : a.getTargetDate().toString());
            m.put("overdue", true);
            actions.add(m);
            actionCodes.add(a.getCode());
        }

        List<Map<String, Object>> defects = new ArrayList<>();
        Set<String> defectCodes = new LinkedHashSet<>();
        for (DefectEntity d : defectRepository.findReopened()) {
            if (defects.size() >= 15) break;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("code", d.getCode());
            m.put("severity", d.getSeverity());
            m.put("status", d.getStatus());
            m.put("itemKey", d.getItemKey());
            m.put("description", truncate(d.getDescription(), 200));
            defects.add(m);
            defectCodes.add(d.getCode());
        }

        return new Evidence(scope, metricsMap, defects, actions, List.of(), historicalContext(5),
                defectCodes, actionCodes, Set.of(), Set.of());
    }

    @Transactional(readOnly = true)
    public Evidence periodEvidence(LocalDate from, LocalDate to) {
        Instant fromTs = from == null ? null : from.atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant toTs = to == null ? null : to.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC).minusMillis(1);
        long submitted = n(metrics.countSubmittedBetween(fromTs, toTs));
        long defectsInPeriod = n(metrics.countDefectsBetween(fromTs, toTs));
        long pendingReviews = n(metrics.countPendingReviews(null));
        long overdueActions = n(metrics.countOverdueActions(null));

        Map<String, Object> scope = new LinkedHashMap<>();
        scope.put("scope", "period report");
        scope.put("from", from == null ? null : from.toString());
        scope.put("to", to == null ? null : to.toString());

        Map<String, Object> metricsMap = new LinkedHashMap<>();
        metricsMap.put("inspectionsSubmitted", submitted);
        metricsMap.put("confirmedDefects", defectsInPeriod);
        metricsMap.put("pendingHumanReviews", pendingReviews);
        metricsMap.put("overdueCorrectiveActions", overdueActions);

        List<Map<String, Object>> defects = new ArrayList<>();
        Set<String> defectCodes = new LinkedHashSet<>();
        for (Object[] row : defectRepository.countByProductAllTime()) {
            // handled in historicalContext
        }
        java.time.Instant fromAll2 = java.time.Instant.EPOCH;
        java.time.Instant toAll2 = java.time.Instant.parse("2999-12-31T23:59:59Z");
        for (DefectEntity d : defectRepository.search(null, null, null, null, null, null, null,
                fromAll2, toAll2, PageRequest.of(0, 15)).getContent()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("code", d.getCode());
            m.put("severity", d.getSeverity());
            m.put("status", d.getStatus());
            m.put("description", truncate(d.getDescription(), 200));
            defects.add(m);
            defectCodes.add(d.getCode());
        }
        List<Map<String, Object>> actions = new ArrayList<>();
        for (ActionEntity a : actionRepository.findOverdue(LocalDate.now())) {
            if (actions.size() >= 10) break;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("code", a.getCode());
            m.put("status", a.getStatus());
            m.put("targetDate", a.getTargetDate() == null ? null : a.getTargetDate().toString());
            actions.add(m);
        }

        return new Evidence(scope, metricsMap, defects, actions, List.of(), historicalContext(5),
                defectCodes, actions.stream().map(a -> String.valueOf(a.get("code")))
                        .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new)),
                Set.of(), Set.of());
    }

    // ---------------------------------------------------------------- facts

    /** Server-computed, verifiable statements — the backbone of every report. */
    public List<String> facts(Evidence evidence) {
        List<String> facts = new ArrayList<>();
        Map<String, Object> scope = evidence.scope();
        Map<String, Object> m = evidence.metrics();

        String batchLabel = scope.containsKey("batchCode")
                ? "Batch " + scope.get("batchCode") + " (" + scope.get("productName") + ")"
                : "Portfolio";
        long inspected = longOf(m.get("distinctItemsInspected"));
        long defective = longOf(m.get("distinctItemsWithConfirmedDefects"));
        long denominator = longOf(m.getOrDefault("rateDenominator", inspected));

        facts.add(batchLabel + ": distinct items inspected = " + inspected
                + (scope.containsKey("quantityProduced")
                        ? " of " + scope.get("quantityProduced") + " produced" : "") + ".");
        if (denominator > 0) {
            double rate = 100.0 * defective / denominator;
            facts.add("Confirmed defective items = " + defective + "; confirmed defect rate = "
                    + String.format(Locale.ROOT, "%.2f", rate) + "% (" + defective + "/" + denominator
                    + ", denominator = distinct inspected items). Model predictions that were not human-confirmed are excluded.");
        } else {
            facts.add("Confirmed defect rate cannot be computed: denominator (distinct inspected items) is 0.");
        }
        if (m.containsKey("distinctItemsReviewed")) {
            facts.add("Distinct items with human review = " + longOf(m.get("distinctItemsReviewed"))
                    + "; pending human reviews = " + longOf(m.getOrDefault("pendingReviews", 0L)) + ".");
        }
        if (m.containsKey("unreviewedModelPredictions")) {
            facts.add("Unreviewed model predictions = " + longOf(m.get("unreviewedModelPredictions"))
                    + " (these are NOT confirmed defects).");
        }
        if (m.containsKey("openCorrectiveActions")) {
            facts.add("Open corrective actions = " + longOf(m.get("openCorrectiveActions"))
                    + "; overdue = " + longOf(m.getOrDefault("overdueCorrectiveActions", 0L)) + ".");
        }
        if (m.containsKey("pendingHumanReviews")) {
            facts.add("Pending human reviews = " + longOf(m.get("pendingHumanReviews"))
                    + "; inspections pending or failed AI processing = "
                    + longOf(m.getOrDefault("pendingOrFailedProcessing", 0L)) + ".");
        }
        if (m.containsKey("inspectionsSubmitted")) {
            facts.add("Inspections submitted in period = " + longOf(m.get("inspectionsSubmitted"))
                    + "; confirmed defects in period = " + longOf(m.getOrDefault("confirmedDefects", 0L)) + ".");
        }
        for (Map<String, Object> d : evidence.defects()) {
            facts.add("Recorded defect " + d.get("code") + " [" + d.get("severity") + "/" + d.get("status")
                    + "] on item " + d.get("itemKey") + ": " + d.get("description"));
        }
        for (Map<String, Object> a : evidence.actions()) {
            facts.add("Corrective action " + a.get("code") + " [" + a.get("status") + "] owner="
                    + a.get("owner") + " target=" + a.get("targetDate")
                    + (Boolean.TRUE.equals(a.get("overdue")) ? " (OVERDUE)" : "") + ": " + a.get("title"));
        }
        for (Map<String, Object> d : evidence.decisions()) {
            facts.add("Batch decision " + d.get("decision") + " by " + d.get("decidedBy")
                    + " at " + d.get("decidedAt") + ": " + d.get("reason"));
        }
        for (Map<String, Object> h : evidence.historicalContext()) {
            facts.add("Historical context: product '" + h.get("productName") + "' has "
                    + h.get("confirmedDefectCount") + " confirmed defect(s) all-time"
                    + (h.get("lastDefectCode") != null ? ", latest " + h.get("lastDefectCode") : "") + ".");
        }
        if (facts.stream().noneMatch(f -> f.startsWith("Recorded defect"))) {
            facts.add("No confirmed defects are recorded in the evidence provided.");
        }
        if (evidence.actions().isEmpty()) {
            facts.add("No corrective actions are present in the evidence provided.");
        }
        return facts;
    }

    // ---------------------------------------------------------------- helpers

    /** "Have we seen similar issues before" grounding: all-time confirmed defects per product. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> historicalContext(int topN) {
        List<Map<String, Object>> context = new ArrayList<>();
        List<Object[]> rows = defectRepository.countByProductAllTime();
        for (int i = 0; i < Math.min(topN, rows.size()); i++) {
            Object[] row = rows.get(i);
            Long productId = (Long) row[0];
            Long count = ((Number) row[1]).longValue();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("productId", productId);
            m.put("productName", productRepository.findById(productId).map(p -> p.getName()).orElse("?"));
            m.put("confirmedDefectCount", count);
            context.add(m);
        }
        return context;
    }

    private Set<String> defectCodesOf(List<Map<String, Object>> defects) {
        Set<String> codes = new LinkedHashSet<>();
        for (Map<String, Object> d : defects) {
            codes.add(String.valueOf(d.get("code")));
        }
        return codes;
    }

    private long n(Long value) {
        return value == null ? 0L : value;
    }

    private long longOf(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private String truncate(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max) + "…";
    }
}
