package com.qualityinspect.backend.ai;

import com.qualityinspect.backend.ai.AiDtos.*;
import com.qualityinspect.backend.security.AuthPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiReportService aiReportService;

    @PostMapping("/reports/batch-summary")
    public AiReportResponse batchSummary(@Valid @RequestBody BatchSummaryRequest request,
                                         @AuthenticationPrincipal AuthPrincipal principal) {
        return aiReportService.batchSummary(request.batchId(), principal);
    }

    @PostMapping("/reports/quality-report")
    public AiReportResponse qualityReport(@Valid @RequestBody QualityReportRequest request,
                                          @AuthenticationPrincipal AuthPrincipal principal) {
        return aiReportService.qualityReport(request.from(), request.to(), request.batchId(), principal);
    }

    @PostMapping("/questions")
    public AiReportResponse question(@Valid @RequestBody QuestionRequest request,
                                     @AuthenticationPrincipal AuthPrincipal principal) {
        return aiReportService.question(request, principal);
    }

    @GetMapping("/reports")
    public List<AiReportResponse> history(@AuthenticationPrincipal AuthPrincipal principal) {
        return aiReportService.history(principal);
    }
}
