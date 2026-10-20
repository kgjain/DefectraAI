package com.qualityinspect.backend.review;

import com.qualityinspect.backend.inspection.InspectionDtos.ReviewResponse;
import com.qualityinspect.backend.review.ReviewDtos.ReviewRequest;
import com.qualityinspect.backend.security.AuthPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    @PostMapping("/api/v1/model-results/{id}/reviews")
    @PreAuthorize("hasAnyRole('INSPECTOR','SUPERVISOR','QUALITY_MANAGER')")
    public ResponseEntity<ReviewResponse> reviewResult(
            @PathVariable long id,
            @Valid @RequestBody ReviewRequest request,
            @AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(reviewService.reviewModelResult(id, request, principal));
    }

    @PostMapping("/api/v1/inspections/{id}/reviews")
    @PreAuthorize("hasAnyRole('INSPECTOR','SUPERVISOR','QUALITY_MANAGER')")
    public ResponseEntity<ReviewResponse> reviewInspection(
            @PathVariable long id,
            @Valid @RequestBody ReviewRequest request,
            @AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(reviewService.reviewInspection(id, request, principal));
    }

    @GetMapping("/api/v1/inspections/{id}/reviews")
    public List<ReviewResponse> list(@PathVariable long id) {
        return reviewService.list(id);
    }
}
