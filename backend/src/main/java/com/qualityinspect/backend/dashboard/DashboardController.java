package com.qualityinspect.backend.dashboard;

import com.qualityinspect.backend.dashboard.DashboardDtos.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/api/v1/dashboard/summary")
    public DashboardSummaryResponse summary() {
        return dashboardService.summary();
    }

    @GetMapping("/api/v1/dashboard/trends")
    public TrendResponse trends(@RequestParam(defaultValue = "30") int days) {
        return dashboardService.trends(days);
    }

    @GetMapping("/api/v1/dashboard/recent")
    public RecentActivityResponse recent() {
        return dashboardService.recent();
    }

    @GetMapping("/api/v1/batches/{id}/quality")
    public BatchQualityResponse batchQuality(@PathVariable long id) {
        return dashboardService.batchQuality(id);
    }

    /** Innovation feature: explainable rule-based prioritization (not machine learning). */
    @GetMapping("/api/v1/review-queue")
    public List<ReviewQueueItemResponse> reviewQueue(@RequestParam(defaultValue = "20") int limit) {
        return dashboardService.reviewQueue(limit);
    }
}
