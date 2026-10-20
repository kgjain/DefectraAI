package com.qualityinspect.backend.review;

import com.qualityinspect.backend.common.audit.AuditService;
import com.qualityinspect.backend.common.error.ApiException.*;
import com.qualityinspect.backend.inspection.*;
import com.qualityinspect.backend.inspection.InspectionDtos.ReviewResponse;
import com.qualityinspect.backend.ml.ModelResultEntity;
import com.qualityinspect.backend.ml.ModelResultRepository;
import com.qualityinspect.backend.review.ReviewDtos.ReviewRequest;
import com.qualityinspect.backend.security.AuthPrincipal;
import com.qualityinspect.backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Human review decisions. Reviews are stored separately from model output —
 * the original ML result is never modified or deleted by a review.
 */
@Service
@RequiredArgsConstructor
public class ReviewService {

    private static final Set<String> SUPERVISOR_OVERRIDES = Set.of("SUPERVISOR", "QUALITY_MANAGER");
    private static final Set<String> REVIEWABLE_ROLES = Set.of("INSPECTOR", "SUPERVISOR", "QUALITY_MANAGER");

    private final InspectionRepository inspectionRepository;
    private final InspectionImageRepository imageRepository;
    private final InspectionReviewRepository reviewRepository;
    private final ModelResultRepository modelResultRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final InspectionService inspectionService;

    @Transactional
    public ReviewResponse reviewModelResult(long modelResultId, ReviewRequest request, AuthPrincipal principal) {
        ModelResultEntity result = modelResultRepository.findById(modelResultId)
                .orElseThrow(() -> new NotFoundException("Model result " + modelResultId + " not found"));
        InspectionImageEntity image = imageRepository.findById(result.getImageId())
                .orElseThrow(() -> new NotFoundException("Image for model result not found"));
        InspectionEntity inspection = inspectionService.require(image.getInspectionId());

        validateRoles(request, principal);
        validateShape(request, result.getPredictedClass());
        rejectDuplicate(result.getId(), null, request.decision(), principal);

        InspectionReviewEntity review = new InspectionReviewEntity();
        review.setInspectionId(inspection.getId());
        review.setModelResultId(result.getId());
        review.setReviewerId(principal.getId());
        applyDecision(review, request, result.getPredictedClass());
        review = reviewRepository.save(review);

        auditService.record("INSPECTION", inspection.getId(), "HUMAN_REVIEW_RECORDED", null,
                Map.of("decision", review.getDecision(),
                        "confirmedOutcome", review.getConfirmedOutcome() == null ? "" : review.getConfirmedOutcome(),
                        "modelResultId", result.getId(),
                        "predictedClass", result.getPredictedClass()));
        return inspectionService.toReviewResponse(review);
    }

    /** Review without a model result (AI unavailable / manual-only inspection). */
    @Transactional
    public ReviewResponse reviewInspection(long inspectionId, ReviewRequest request, AuthPrincipal principal) {
        InspectionEntity inspection = inspectionService.require(inspectionId);
        if (!"SUBMITTED".equals(inspection.getStatus())) {
            throw new ConflictException("Only submitted inspections can be reviewed");
        }
        validateRoles(request, principal);
        if ("CORRECT".equals(request.decision())) {
            throw new BadRequestException("CORRECT applies to a model prediction; use CONFIRM with an explicit outcome");
        }
        if ("CONFIRM".equals(request.decision())
                && (request.confirmedOutcome() == null || request.confirmedOutcome().isBlank())) {
            throw new BadRequestException("confirmedOutcome is required when reviewing without a model result");
        }
        rejectDuplicate(null, inspectionId, request.decision(), principal);

        InspectionReviewEntity review = new InspectionReviewEntity();
        review.setInspectionId(inspectionId);
        review.setReviewerId(principal.getId());
        applyDecision(review, request, null);
        review = reviewRepository.save(review);

        auditService.record("INSPECTION", inspectionId, "HUMAN_REVIEW_RECORDED",
                request.reason(), Map.of("decision", review.getDecision(),
                        "confirmedOutcome", review.getConfirmedOutcome() == null ? "" : review.getConfirmedOutcome(),
                        "modelResultId", "manual"));
        return inspectionService.toReviewResponse(review);
    }

    @Transactional(readOnly = true)
    public List<ReviewResponse> list(long inspectionId) {
        inspectionService.require(inspectionId);
        return reviewRepository.findByInspectionIdOrderByCreatedAtAsc(inspectionId).stream()
                .map(inspectionService::toReviewResponse)
                .toList();
    }

    // ---------------------------------------------------------------- rules

    private void validateRoles(ReviewRequest request, AuthPrincipal principal) {
        Set<String> roles = principal.getRoles();
        if (roles.stream().noneMatch(REVIEWABLE_ROLES::contains)) {
            throw new ForbiddenException("Your role is not permitted to record human reviews");
        }
        if ("CORRECT".equals(request.decision()) && roles.stream().noneMatch(SUPERVISOR_OVERRIDES::contains)) {
            throw new ForbiddenException("Only supervisors and quality managers may override a model prediction");
        }
    }

    private void validateShape(ReviewRequest request, String predictedClass) {
        switch (request.decision()) {
            case "CONFIRM" -> { /* outcome copied from the prediction */ }
            case "CORRECT" -> {
                if (request.confirmedOutcome() == null || request.confirmedOutcome().isBlank()) {
                    throw new BadRequestException("confirmedOutcome is required when correcting a prediction");
                }
                if (!Set.of("GOOD", "POTENTIALLY_DEFECTIVE").contains(request.confirmedOutcome())) {
                    throw new BadRequestException("Corrected outcome must be GOOD or POTENTIALLY_DEFECTIVE");
                }
                if (request.reason() == null || request.reason().isBlank()) {
                    throw new BadRequestException("An explanation is mandatory when correcting or overriding a prediction");
                }
            }
            case "MANUAL_REQUIRED" -> { /* reason optional */ }
            case "EVIDENCE_REQUESTED" -> {
                if (request.evidenceRequestedNote() == null || request.evidenceRequestedNote().isBlank()) {
                    throw new BadRequestException("Describe the additional evidence you need");
                }
            }
            default -> throw new BadRequestException("Unknown decision");
        }
    }

    private void rejectDuplicate(Long modelResultId, Long inspectionId, String decision, AuthPrincipal principal) {
        if (modelResultId != null) {
            boolean duplicate = reviewRepository.findByModelResultIdOrderByCreatedAtAsc(modelResultId).stream()
                    .anyMatch(r -> r.getReviewerId().equals(principal.getId()) && r.getDecision().equals(decision));
            if (duplicate) {
                throw new ConflictException("You already recorded a '" + decision + "' review for this result");
            }
        } else {
            boolean duplicate = reviewRepository.findByInspectionIdOrderByCreatedAtAsc(inspectionId).stream()
                    .anyMatch(r -> r.getReviewerId().equals(principal.getId())
                            && r.getDecision().equals(decision) && r.getModelResultId() == null);
            if (duplicate) {
                throw new ConflictException("You already recorded a '" + decision + "' review for this inspection");
            }
        }
    }

    private void applyDecision(InspectionReviewEntity review, ReviewRequest request, String predictedClass) {
        review.setDecision(request.decision());
        switch (request.decision()) {
            case "CONFIRM" -> review.setConfirmedOutcome(
                    predictedClass != null ? predictedClass : request.confirmedOutcome());
            case "CORRECT" -> {
                review.setConfirmedOutcome(request.confirmedOutcome());
                review.setReason(request.reason().trim());
            }
            case "MANUAL_REQUIRED" -> {
                review.setConfirmedOutcome("NEEDS_MANUAL");
                if (request.reason() != null && !request.reason().isBlank()) {
                    review.setReason(request.reason().trim());
                }
            }
            case "EVIDENCE_REQUESTED" -> review.setEvidenceRequestedNote(request.evidenceRequestedNote().trim());
            default -> throw new BadRequestException("Unknown decision");
        }
    }

    /** Convenience used by defect creation: does this inspection carry a human-confirmed defect outcome? */
    @Transactional(readOnly = true)
    public boolean hasConfirmedDefectOutcome(long inspectionId) {
        return reviewRepository.findByInspectionIdOrderByCreatedAtAsc(inspectionId).stream()
                .anyMatch(r -> "POTENTIALLY_DEFECTIVE".equals(r.getConfirmedOutcome()));
    }
}
