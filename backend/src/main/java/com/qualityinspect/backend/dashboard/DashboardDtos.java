package com.qualityinspect.backend.dashboard;

import com.qualityinspect.backend.action.ActionDtos.ActionResponse;
import com.qualityinspect.backend.decision.DecisionDtos.BatchDecisionResponse;
import com.qualityinspect.backend.defect.DefectDtos.DefectResponse;
import com.qualityinspect.backend.inspection.InspectionDtos.InspectionResponse;

import java.time.LocalDate;
import java.util.List;

public final class DashboardDtos {

    private DashboardDtos() {}

    public record DashboardSummaryResponse(
            long activeProducts,
            long activeBatches,
            long distinctItemsInspected,
            long distinctItemsReviewed,
            long pendingReviews,
            long pendingProcessing,
            long distinctItemsWithConfirmedDefects,
            Double confirmedDefectRate,
            long rateNumerator,
            long rateDenominator,
            long openCorrectiveActions,
            long overdueCorrectiveActions) {}

    public record TrendPoint(LocalDate date, long inspectionsSubmitted, long confirmedDefects) {}

    public record TrendResponse(int days, List<TrendPoint> points) {}

    public record RecentActivityResponse(
            List<InspectionResponse> recentInspections,
            List<DefectResponse> recentlyConfirmedDefects,
            List<ActionResponse> actionsNeedingAttention) {}

    public record BatchQualityResponse(
            long batchId,
            String batchCode,
            String productName,
            int quantityProduced,
            long distinctItemsInspected,
            long distinctItemsReviewed,
            long distinctItemsWithConfirmedDefects,
            long pendingProcessing,
            long pendingReviews,
            long openCorrectiveActions,
            Double confirmedDefectRate,
            long rateNumerator,
            long rateDenominator,
            String currentDecision,
            List<BatchDecisionResponse> decisionHistory) {}

    public record PriorityReason(String factor, int weight, String detail) {}

    public record ReviewQueueItemResponse(
            long inspectionId,
            String batchCode,
            String productCode,
            String productName,
            String itemReference,
            String aiState,
            String reviewState,
            int priorityScore,
            List<PriorityReason> reasons) {}
}
