package com.qualityinspect.backend.defect;

import com.qualityinspect.backend.audit.AuditQueryService;
import com.qualityinspect.backend.batch.BatchRepository;
import com.qualityinspect.backend.common.audit.AuditService;
import com.qualityinspect.backend.common.error.ApiException.*;
import com.qualityinspect.backend.common.web.PageResponse;
import com.qualityinspect.backend.defect.DefectDtos.*;
import com.qualityinspect.backend.inspection.InspectionDtos.ImageResponse;
import com.qualityinspect.backend.inspection.InspectionEntity;
import com.qualityinspect.backend.inspection.InspectionImageEntity;
import com.qualityinspect.backend.inspection.InspectionImageRepository;
import com.qualityinspect.backend.inspection.InspectionService;
import com.qualityinspect.backend.product.ProductRepository;
import com.qualityinspect.backend.review.ReviewService;
import com.qualityinspect.backend.security.AuthPrincipal;
import com.qualityinspect.backend.action.ActionRepository;
import com.qualityinspect.backend.common.idempotency.IdempotencyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DefectService {

    private static final Set<String> MANAGERS = Set.of("QUALITY_MANAGER");
    private static final Set<String> SENIOR = Set.of("SUPERVISOR", "QUALITY_MANAGER");

    private final DefectRepository defectRepository;
    private final DefectEvidenceRepository evidenceRepository;
    private final InspectionImageRepository imageRepository;
    private final InspectionService inspectionService;
    private final ReviewService reviewService;
    private final BatchRepository batchRepository;
    private final ProductRepository productRepository;
    private final com.qualityinspect.backend.user.UserRepository userRepository;
    private final ActionRepository actionRepository;
    private final AuditService auditService;
    private final AuditQueryService auditQueryService;
    private final IdempotencyService idempotencyService;

    // ---------------------------------------------------------------- create

    @Transactional
    public DefectResponse create(DefectCreateRequest request, AuthPrincipal principal, String idempotencyKey) {
        return idempotencyService.execute(idempotencyKey, "POST /defects", request, DefectResponse.class,
                () -> doCreate(request, principal));
    }

    private DefectResponse doCreate(DefectCreateRequest request, AuthPrincipal principal) {
        InspectionEntity inspection = inspectionService.require(request.inspectionId());
        if (!"SUBMITTED".equals(inspection.getStatus())) {
            throw new ConflictException("Only submitted inspections can produce defects");
        }
        if (defectRepository.existsByInspectionId(inspection.getId())) {
            throw new ConflictException("A defect is already recorded for inspection " + inspection.getId());
        }
        if (!reviewService.hasConfirmedDefectOutcome(inspection.getId())) {
            throw new ConflictException(
                    "A human review must confirm a potentially defective outcome before a defect can be recorded");
        }

        List<Long> evidenceImageIds = resolveEvidenceImages(request, inspection);

        DefectEntity defect = new DefectEntity();
        defect.setInspectionId(inspection.getId());
        defect.setBatchId(inspection.getBatchId());
        defect.setProductId(inspection.getProductId());
        defect.setItemKey(inspection.getItemKey() != null
                ? inspection.getItemKey() : inspection.getItemReference().toUpperCase(Locale.ROOT));
        defect.setDescription(request.description().trim());
        defect.setSeverity(request.severity());
        defect.setCategory(blankToNull(request.category()));
        defect.setConfirmationReason(request.confirmationReason());
        defect.setAssignedReviewerId(request.assignedReviewerId());
        defect.setCreatedBy(principal.getId());
        defect.setStatus("OPEN");
        defect.setReviewStatus("CONFIRMED");
        defect = saveWithUniqueCode(defect);

        for (Long imageId : evidenceImageIds) {
            DefectEvidenceEntity evidence = new DefectEvidenceEntity();
            evidence.setId(new DefectEvidenceId(defect.getId(), imageId));
            evidence.setAddedBy(principal.getId());
            evidenceRepository.save(evidence);
        }

        auditService.record("DEFECT", defect.getId(), "DEFECT_CONFIRMED",
                request.confirmationReason(),
                Map.of("code", defect.getCode(), "inspectionId", inspection.getId(),
                        "severity", defect.getSeverity(), "itemKey", defect.getItemKey()));
        return toResponse(defect);
    }

    // ---------------------------------------------------------------- update

    @Transactional
    public DefectResponse update(long id, DefectUpdateRequest request, AuthPrincipal principal) {
        DefectEntity defect = require(id);
        boolean senior = principal.getRoles().stream().anyMatch(SENIOR::contains);
        boolean manager = principal.getRoles().stream().anyMatch(MANAGERS::contains);

        boolean changesConfirmedFinding = request.description() != null || request.severity() != null
                || request.category() != null;
        if (changesConfirmedFinding && !senior) {
            throw new ForbiddenException("Only supervisors and quality managers may alter a confirmed defect");
        }

        if (request.description() != null && !request.description().isBlank()) {
            defect.setDescription(request.description().trim());
        }
        if (request.severity() != null && !request.severity().isBlank()) {
            defect.setSeverity(request.severity());
        }
        if (request.category() != null) {
            defect.setCategory(blankToNull(request.category()));
        }
        if (request.assignedReviewerId() != null) {
            if (!manager) {
                throw new ForbiddenException("Only quality managers may assign reviewers");
            }
            userRepository.findById(request.assignedReviewerId())
                    .orElseThrow(() -> new NotFoundException("Reviewer not found"));
            defect.setAssignedReviewerId(request.assignedReviewerId());
        }
        if (request.reviewStatus() != null) {
            if (!manager) {
                throw new ForbiddenException("Only quality managers may change the review status of a defect");
            }
            defect.setReviewStatus(request.reviewStatus());
        }
        if (request.status() != null) {
            applyStatusChange(defect, request.status(), request.reason(), principal, manager);
        }

        defect = defectRepository.save(defect);
        if (request.description() != null || request.severity() != null || request.category() != null) {
            auditService.record("DEFECT", defect.getId(), "DEFECT_UPDATED", request.reason(),
                    Map.of("severity", defect.getSeverity()));
        }
        return toResponse(defect);
    }

    /** Exposed for unit testing: allowed status transitions. */
    static boolean isValidTransition(String current, String target) {
        return ("OPEN".equals(current) && "CLOSED".equals(target))
                || ("REOPENED".equals(current) && "CLOSED".equals(target))
                || ("CLOSED".equals(current) && "REOPENED".equals(target));
    }

    private void applyStatusChange(DefectEntity defect, String target, String reason, AuthPrincipal principal,
                                   boolean manager) {
        if (!manager) {
            throw new ForbiddenException("Only quality managers may close or reopen defects");
        }
        String current = defect.getStatus();
        if (target.equals(current)) {
            throw new ConflictException("Defect is already in status " + target);
        }
        if (!isValidTransition(current, target)) {
            throw new ConflictException("Invalid defect transition " + current + " -> " + target);
        }
        if (reason == null || reason.isBlank()) {
            throw new BadRequestException("A reason is required to close or reopen a defect");
        }
        if ("CLOSED".equals(target)) {
            defect.setStatus("CLOSED");
            defect.setClosureReason(reason.trim());
            defect.setClosedBy(principal.getId());
            defect.setClosedAt(Instant.now());
            auditService.record("DEFECT", defect.getId(), "DEFECT_CLOSED", reason.trim(),
                    Map.of("code", defect.getCode()));
        } else {
            // prior closure fields are intentionally preserved - history is never erased
            defect.setStatus("REOPENED");
            auditService.record("DEFECT", defect.getId(), "DEFECT_REOPENED", reason.trim(),
                    Map.of("code", defect.getCode(),
                            "previousClosureReason", defect.getClosureReason() == null ? "" : defect.getClosureReason()));
        }
    }

    // ---------------------------------------------------------------- read

    @Transactional(readOnly = true)
    public PageResponse<DefectResponse> list(String search, Long batchId, Long productId, String status,
                                             String severity, String category,
                                             java.time.LocalDate from, java.time.LocalDate to,
                                             int page, int size) {
        // sentinel range instead of null-checked params: PostgreSQL cannot infer the
        // type of a null parameter used only in "(:x is null or ...)" comparisons
        Instant fromTs = from == null ? Instant.EPOCH
                : from.atStartOfDay(java.time.ZoneOffset.UTC).toInstant();
        Instant toTs = to == null ? Instant.parse("2999-12-31T23:59:59Z")
                : to.plusDays(1).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().minusMillis(1);
        var result = defectRepository.search(blankToNull(search),
                blankToNull(search == null ? null : search.toUpperCase(java.util.Locale.ROOT)),
                batchId, productId, blankToNull(status),
                blankToNull(severity), blankToNull(category), fromTs, toTs,
                PageRequest.of(page, Math.min(size, 100)));
        return PageResponse.from(result.map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public DefectDetailResponse detail(long id) {
        DefectEntity defect = require(id);
        List<Long> evidenceImageIds = evidenceRepository.findByIdDefectId(id).stream()
                .map(e -> e.getId().getImageId()).toList();
        List<ImageResponse> evidence = imageRepository.findAllById(evidenceImageIds).stream()
                .map(img -> new ImageResponse(img.getId(), img.getInspectionId(), img.getOriginalFilename(),
                        img.getContentType(), img.getSizeBytes(), img.getSha256(), img.getVersion(),
                        img.isCurrent(), img.getReplacedById(), "/api/v1/images/" + img.getId(), img.getCreatedAt()))
                .toList();

        List<ActionSummaryResponse> actions = actionRepository.findByDefectIdOrderByIdAsc(id).stream()
                .map(a -> new ActionSummaryResponse(
                        a.getId(), a.getCode(), a.getTitle(), a.getStatus(),
                        userRepository.findById(a.getOwnerId()).map(u -> u.getDisplayName()).orElse("?"),
                        a.getTargetDate(), a.isOverdue()))
                .toList();

        return new DefectDetailResponse(toResponse(defect), evidence, actions,
                auditQueryService.history("DEFECT", id, 50));
    }

    public DefectEntity require(long id) {
        return defectRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Defect " + id + " not found"));
    }

    public DefectResponse toResponse(DefectEntity defect) {
        String batchCode = batchRepository.findById(defect.getBatchId()).map(b -> b.getCode()).orElse("?");
        String productName = productRepository.findById(defect.getProductId()).map(p -> p.getName()).orElse("?");
        String assigned = defect.getAssignedReviewerId() == null ? null
                : userRepository.findById(defect.getAssignedReviewerId()).map(u -> u.getUsername()).orElse(null);
        String creator = defect.getCreatedBy() == null ? null
                : userRepository.findById(defect.getCreatedBy()).map(u -> u.getUsername()).orElse(null);
        return new DefectResponse(
                defect.getId(), defect.getCode(), defect.getInspectionId(), defect.getBatchId(), batchCode,
                defect.getProductId(), productName, defect.getItemKey(), defect.getCategory(),
                defect.getDescription(), defect.getSeverity(), defect.getStatus(), defect.getReviewStatus(),
                assigned, creator, defect.getClosureReason(), defect.getClosedAt(),
                defect.getCreatedAt(), defect.getUpdatedAt());
    }

    // ---------------------------------------------------------------- helpers

    private List<Long> resolveEvidenceImages(DefectCreateRequest request, InspectionEntity inspection) {
        if (request.imageIds() != null && !request.imageIds().isEmpty()) {
            List<InspectionImageEntity> images = imageRepository.findAllById(request.imageIds());
            if (images.size() != request.imageIds().size()) {
                throw new BadRequestException("One or more evidence images do not exist");
            }
            for (InspectionImageEntity image : images) {
                if (!image.getInspectionId().equals(inspection.getId())) {
                    throw new BadRequestException("Evidence images must belong to the same inspection");
                }
            }
            return images.stream().map(InspectionImageEntity::getId).toList();
        }
        return imageRepository.findByInspectionIdAndCurrentTrue(inspection.getId()).stream()
                .map(InspectionImageEntity::getId).collect(Collectors.toList());
    }

    private DefectEntity saveWithUniqueCode(DefectEntity defect) {
        RuntimeException last = null;
        for (int attempt = 0; attempt < 5; attempt++) {
            defect.setCode(String.format("DEF-%05d", defectRepository.maxId() + 1 + attempt));
            try {
                return defectRepository.saveAndFlush(defect);
            } catch (DataIntegrityViolationException ex) {
                last = ex;
            }
        }
        throw new ConflictException("Could not allocate a unique defect code; please retry");
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
