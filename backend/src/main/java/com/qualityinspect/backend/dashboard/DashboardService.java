package com.qualityinspect.backend.dashboard;

import com.qualityinspect.backend.action.ActionDtos.ActionResponse;
import com.qualityinspect.backend.action.ActionEntity;
import com.qualityinspect.backend.action.ActionRepository;
import com.qualityinspect.backend.action.ActionService;
import com.qualityinspect.backend.batch.BatchEntity;
import com.qualityinspect.backend.batch.BatchRepository;
import com.qualityinspect.backend.common.error.ApiException.NotFoundException;
import com.qualityinspect.backend.common.web.PageResponse;
import com.qualityinspect.backend.dashboard.DashboardDtos.*;
import com.qualityinspect.backend.decision.BatchDecisionRepository;
import com.qualityinspect.backend.decision.DecisionDtos.BatchDecisionResponse;
import com.qualityinspect.backend.decision.DecisionService;
import com.qualityinspect.backend.defect.DefectDtos.DefectResponse;
import com.qualityinspect.backend.defect.DefectEntity;
import com.qualityinspect.backend.defect.DefectRepository;
import com.qualityinspect.backend.defect.DefectService;
import com.qualityinspect.backend.inspection.*;
import com.qualityinspect.backend.inspection.InspectionDtos.InspectionResponse;
import com.qualityinspect.backend.ml.ModelResultEntity;
import com.qualityinspect.backend.ml.ModelResultRepository;
import com.qualityinspect.backend.ml.ProcessingAttemptEntity;
import com.qualityinspect.backend.ml.ProcessingAttemptRepository;
import com.qualityinspect.backend.product.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Dashboard metrics and the explainable Smart Review Prioritization queue.
 * Every number is computed from persisted records (no hard-coded samples).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardService {

    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE;

    private final MetricQueries metrics;
    private final ProductRepository productRepository;
    private final BatchRepository batchRepository;
    private final InspectionRepository inspectionRepository;
    private final InspectionImageRepository imageRepository;
    private final InspectionReviewRepository reviewRepository;
    private final ModelResultRepository modelResultRepository;
    private final ProcessingAttemptRepository attemptRepository;
    private final DefectRepository defectRepository;
    private final ActionRepository actionRepository;
    private final ActionService actionService;
    private final DefectService defectService;
    private final InspectionService inspectionService;
    private final DecisionService decisionService;

    // ---------------------------------------------------------------- summary

    @Transactional(readOnly = true)
    public DashboardSummaryResponse summary() {
        long activeProducts = productRepository.countByStatus("ACTIVE");
        long activeBatches = batchRepository.countByStatus("ACTIVE");
        long inspected = n(metrics.countDistinctItemsInspected(null));
        long reviewed = n(metrics.countDistinctItemsReviewed(null));
        long pendingReviews = n(metrics.countPendingReviews(null));
        long pendingProcessing = n(metrics.countAwaitingOrFailedProcessing(null));
        long defectiveItems = n(metrics.countDistinctDefectiveItems(null));
        long openActions = n(metrics.countOpenActions(null));
        long overdueActions = n(metrics.countOverdueActions(null));
        Double rate = inspected > 0 ? round4((double) defectiveItems / inspected) : null;
        return new DashboardSummaryResponse(activeProducts, activeBatches, inspected, reviewed,
                pendingReviews, pendingProcessing, defectiveItems, rate,
                defectiveItems, inspected, openActions, overdueActions);
    }

    // ---------------------------------------------------------------- trends

    @Transactional(readOnly = true)
    public TrendResponse trends(int days) {
        int safeDays = Math.max(1, Math.min(days, 365));
        LocalDate start = LocalDate.now(ZoneOffset.UTC).minusDays(safeDays - 1L);
        var from = start.atStartOfDay().toInstant(ZoneOffset.UTC);

        Map<String, Long> submitted = toMap(metrics.dailySubmitted(from));
        Map<String, Long> defects = toMap(metrics.dailyDefects(from));

        List<TrendPoint> points = new ArrayList<>();
        for (LocalDate d = start; !d.isAfter(LocalDate.now(ZoneOffset.UTC)); d = d.plusDays(1)) {
            String key = d.format(ISO_DATE);
            points.add(new TrendPoint(d, submitted.getOrDefault(key, 0L), defects.getOrDefault(key, 0L)));
        }
        return new TrendResponse(safeDays, points);
    }

    // ---------------------------------------------------------------- recent

    @Transactional(readOnly = true)
    public RecentActivityResponse recent() {
        PageResponse<InspectionResponse> inspections =
                inspectionService.list(null, null, "SUBMITTED", null, null, null, null,
                        null, null, 0, 5);
        PageResponse<DefectResponse> defects =
                defectService.list(null, null, null, "OPEN", null, null, null, null, 0, 5);
        List<ActionResponse> attention = actionService.list(null, null, null, null, true, 0, 8).content();
        return new RecentActivityResponse(inspections.content(), defects.content(), attention);
    }

    // ---------------------------------------------------------------- batch quality

    @Transactional(readOnly = true)
    public BatchQualityResponse batchQuality(long batchId) {
        BatchEntity batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new NotFoundException("Batch " + batchId + " not found"));
        long inspected = n(metrics.countDistinctItemsInspected(batchId));
        long reviewed = n(metrics.countDistinctItemsReviewed(batchId));
        long defectiveItems = n(metrics.countDistinctDefectiveItems(batchId));
        long pendingProcessing = n(metrics.countAwaitingOrFailedProcessing(batchId));
        long pendingReviews = n(metrics.countPendingReviews(batchId));
        long openActions = n(metrics.countOpenActions(batchId));
        Double rate = inspected > 0 ? round4((double) defectiveItems / inspected) : null;

        List<BatchDecisionResponse> history = decisionService.history(batchId);
        String current = history.isEmpty() ? null : history.get(0).decision();
        String productName = productRepository.findById(batch.getProductId())
                .map(p -> p.getName()).orElse("?");

        return new BatchQualityResponse(batchId, batch.getCode(), productName, batch.getQuantityProduced(),
                inspected, reviewed, defectiveItems, pendingProcessing, pendingReviews, openActions,
                rate, defectiveItems, inspected, current, history);
    }

    // ------------------------------------------------- innovation: review queue

    /**
     * Smart Review Prioritization — a transparent, rule-based ordering (NOT machine
     * learning). Each item returns its score and the exact factors behind it so an
     * authorized user can verify every underlying record.
     */
    @Transactional(readOnly = true)
    public List<ReviewQueueItemResponse> reviewQueue(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 50));
        List<InspectionEntity> candidates = inspectionRepository
                .search(null, null, "SUBMITTED", null, null, null, null, null, null,
                        PageRequest.of(0, 60))
                .getContent();
        if (candidates.isEmpty()) {
            return List.of();
        }

        List<Long> ids = candidates.stream().map(InspectionEntity::getId).toList();
        List<InspectionImageEntity> images = imageRepository.findByInspectionIdInAndCurrentTrue(ids);
        List<Long> imageIds = images.stream().map(InspectionImageEntity::getId).toList();
        List<ProcessingAttemptEntity> attempts = imageIds.isEmpty() ? List.of()
                : attemptRepository.findByImageIdIn(imageIds);
        List<ModelResultEntity> results = imageIds.isEmpty() ? List.of()
                : modelResultRepository.findByImageIdInAndCurrentTrue(imageIds);
        Set<Long> reviewed = reviewRepositorySet(ids);
        Set<Long> reopened = new HashSet<>(defectRepository.findReopenedInspectionIds());

        Map<Long, List<InspectionImageEntity>> imagesByInspection =
                images.stream().collect(HashMap::new, (m, v) -> m.computeIfAbsent(v.getInspectionId(),
                        k -> new ArrayList<>()).add(v), HashMap::putAll);
        Map<Long, List<ProcessingAttemptEntity>> attemptsByImage =
                attempts.stream().collect(HashMap::new, (m, v) -> m.computeIfAbsent(v.getImageId(),
                        k -> new ArrayList<>()).add(v), HashMap::putAll);
        Map<Long, List<ModelResultEntity>> resultsByImage =
                results.stream().collect(HashMap::new, (m, v) -> m.computeIfAbsent(v.getImageId(),
                        k -> new ArrayList<>()).add(v), HashMap::putAll);

        // repeat confirmed issues per product family (last 30 days)
        Map<Long, Long> productDefectCounts = new HashMap<>();
        for (Object[] row : defectRepository.countByProductSince(
                java.time.Instant.now().minus(30, ChronoUnit.DAYS))) {
            productDefectCounts.put((Long) row[0], (Long) row[1]);
        }

        // batches with overdue corrective actions
        Set<Long> overdueActionBatchIds = new HashSet<>();
        List<ActionEntity> overdue = actionRepository.findOverdue(LocalDate.now());
        if (!overdue.isEmpty()) {
            List<Long> defectIds = overdue.stream().map(ActionEntity::getDefectId).distinct().toList();
            overdueActionBatchIds.addAll(defectRepository.findBatchIdsByDefectIds(defectIds));
        }

        Map<Long, String> batchCodes = batchRepository.findAllById(
                        candidates.stream().map(InspectionEntity::getBatchId).distinct().toList()).stream()
                .collect(HashMap::new, (m, b) -> m.put(b.getId(), b.getCode()), HashMap::putAll);
        Map<Long, BatchEntity> batchesById = batchRepository.findAllById(
                        candidates.stream().map(InspectionEntity::getBatchId).distinct().toList()).stream()
                .collect(HashMap::new, (m, b) -> m.put(b.getId(), b), HashMap::putAll);

        List<ReviewQueueItemResponse> queue = new ArrayList<>();
        for (InspectionEntity inspection : candidates) {
            List<PriorityReason> reasons = new ArrayList<>();
            int score = 0;

            List<InspectionImageEntity> inspImages =
                    imagesByInspection.getOrDefault(inspection.getId(), List.of());
            List<ProcessingAttemptEntity> inspAttempts = new ArrayList<>();
            for (InspectionImageEntity image : inspImages) {
                inspAttempts.addAll(attemptsByImage.getOrDefault(image.getId(), List.of()));
            }
            List<ModelResultEntity> inspResults = new ArrayList<>();
            for (InspectionImageEntity image : inspImages) {
                inspResults.addAll(resultsByImage.getOrDefault(image.getId(), List.of()));
            }

            long failed = inspAttempts.stream().filter(a -> "FAILED".equals(a.getStatus())).count();
            if (failed > 0 && inspResults.isEmpty()) {
                score += 40;
                reasons.add(new PriorityReason("FAILED_PROCESSING", 40,
                        "AI processing failed after " + failed + " attempt(s); retry or manual review needed"));
            }
            if (inspImages.isEmpty()) {
                score += 35;
                reasons.add(new PriorityReason("NO_EVIDENCE", 35, "No current evidence image attached"));
            } else {
                boolean tiny = inspImages.stream().anyMatch(i -> i.getSizeBytes() < 20_480);
                if (tiny) {
                    score += 15;
                    reasons.add(new PriorityReason("POOR_EVIDENCE", 15,
                            "At least one evidence file is under 20 KB and may be insufficient"));
                }
            }
            boolean isReviewed = reviewed.contains(inspection.getId());
            if (!isReviewed && !inspResults.isEmpty()) {
                score += 25;
                String prediction = inspResults.get(0).getPredictedClass();
                reasons.add(new PriorityReason("UNREVIEWED_PREDICTION", 25,
                        "Model prediction '" + prediction + "' ("
                                + inspResults.get(0).getModelName() + " "
                                + inspResults.get(0).getModelVersion() + ") is awaiting human review"));
                if (!"OK".equals(prediction)) {
                    score += 10;
                    reasons.add(new PriorityReason("NEGATIVE_PREDICTION", 10,
                            "Prediction '" + prediction + "' flags a potentially defective item"));
                }
            }
            if (reopened.contains(inspection.getId())) {
                score += 35;
                reasons.add(new PriorityReason("REOPENED_DEFECT", 35,
                        "A defect on this item was reopened after follow-up"));
            }
            Long productCount = productDefectCounts.get(inspection.getProductId());
            if (productCount != null && productCount >= 2) {
                score += 20;
                reasons.add(new PriorityReason("REPEAT_ISSUE", 20,
                        productCount + " confirmed defects on this product family in the last 30 days"));
            }
            if (overdueActionBatchIds.contains(inspection.getBatchId())) {
                score += 15;
                reasons.add(new PriorityReason("OVERDUE_ACTION", 15,
                        "This batch has overdue corrective actions"));
            }

            BatchEntity batch = batchesById.get(inspection.getBatchId());
            queue.add(new ReviewQueueItemResponse(
                    inspection.getId(),
                    batchCodes.getOrDefault(inspection.getBatchId(), "?"),
                    batch == null ? "?" : productCode(batch),
                    batch == null ? "?" : productName(batch),
                    inspection.getItemReference(),
                    InspectionService.computeAiState(inspImages, attemptsByImage, resultsByImage),
                    isReviewed ? "REVIEWED" : "PENDING",
                    score,
                    reasons));
        }
        queue.sort(Comparator.comparingInt(ReviewQueueItemResponse::priorityScore).reversed());
        return queue.stream().limit(safeLimit).toList();
    }

    // ---------------------------------------------------------------- helpers

    private Set<Long> reviewRepositorySet(List<Long> inspectionIds) {
        return inspectionIds.isEmpty() ? Set.of()
                : reviewRepository.findByInspectionIdIn(inspectionIds).stream()
                        .map(InspectionReviewEntity::getInspectionId)
                        .collect(java.util.stream.Collectors.toSet());
    }

    private String productCode(BatchEntity batch) {
        return productRepository.findById(batch.getProductId()).map(p -> p.getCode()).orElse("?");
    }

    private String productName(BatchEntity batch) {
        return productRepository.findById(batch.getProductId()).map(p -> p.getName()).orElse("?");
    }

    private long n(Long value) {
        return value == null ? 0L : value;
    }

    private Double round4(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }

    private Map<String, Long> toMap(List<Object[]> rows) {
        Map<String, Long> map = new HashMap<>();
        for (Object[] row : rows) {
            map.put(String.valueOf(row[0]), ((Number) row[1]).longValue());
        }
        return map;
    }
}
