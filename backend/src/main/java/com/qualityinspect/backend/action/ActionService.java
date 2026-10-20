package com.qualityinspect.backend.action;

import com.qualityinspect.backend.audit.AuditQueryService;
import com.qualityinspect.backend.action.ActionDtos.*;
import com.qualityinspect.backend.common.audit.AuditService;
import com.qualityinspect.backend.common.error.ApiException.*;
import com.qualityinspect.backend.common.idempotency.IdempotencyService;
import com.qualityinspect.backend.common.web.PageResponse;
import com.qualityinspect.backend.defect.DefectEntity;
import com.qualityinspect.backend.defect.DefectRepository;
import com.qualityinspect.backend.defect.DefectService;
import com.qualityinspect.backend.inspection.InspectionDtos.InspectionResponse;
import com.qualityinspect.backend.inspection.InspectionService;
import com.qualityinspect.backend.review.ReviewService;
import com.qualityinspect.backend.security.AuthPrincipal;
import com.qualityinspect.backend.user.UserEntity;
import com.qualityinspect.backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ActionService {

    private static final Map<String, Set<String>> ALLOWED_TRANSITIONS = Map.of(
            "OPEN", Set.of("IN_PROGRESS", "CANCELLED"),
            "IN_PROGRESS", Set.of("AWAITING_REINSPECTION", "COMPLETED", "CANCELLED"),
            "AWAITING_REINSPECTION", Set.of("COMPLETED", "IN_PROGRESS"),
            "COMPLETED", Set.of(),
            "CANCELLED", Set.of());

    private static final Set<String> SENIOR = Set.of("SUPERVISOR", "QUALITY_MANAGER");
    private static final Set<String> MANAGERS = Set.of("QUALITY_MANAGER");

    private final ActionRepository actionRepository;
    private final DefectRepository defectRepository;
    private final DefectService defectService;
    private final UserRepository userRepository;
    private final InspectionService inspectionService;
    private final ReviewService reviewService;
    private final AuditService auditService;
    private final AuditQueryService auditQueryService;
    private final IdempotencyService idempotencyService;

    // ---------------------------------------------------------------- create

    @Transactional
    public ActionResponse create(ActionCreateRequest request, AuthPrincipal principal, String idempotencyKey) {
        return idempotencyService.execute(idempotencyKey, "POST /actions", request, ActionResponse.class,
                () -> doCreate(request, principal));
    }

    private ActionResponse doCreate(ActionCreateRequest request, AuthPrincipal principal) {
        DefectEntity defect = defectRepository.findById(request.defectId())
                .orElseThrow(() -> new NotFoundException("Defect " + request.defectId() + " not found"));
        if ("CLOSED".equals(defect.getStatus())) {
            throw new ConflictException("Cannot assign corrective work to a closed defect");
        }
        UserEntity owner = userRepository.findById(request.ownerId())
                .orElseThrow(() -> new NotFoundException("Owner user not found"));
        if (!owner.isEnabled()) {
            throw new BadRequestException("The selected owner is disabled");
        }

        ActionEntity action = new ActionEntity();
        action.setDefectId(defect.getId());
        action.setTitle(request.title().trim());
        action.setDescription(request.description());
        action.setOwnerId(owner.getId());
        action.setTargetDate(request.targetDate());
        action.setCreatedBy(principal.getId());
        action = saveWithUniqueCode(action);

        auditService.record("ACTION", action.getId(), "ACTION_CREATED", null,
                Map.of("code", action.getCode(), "defectId", defect.getId(),
                        "ownerId", owner.getId(), "targetDate", action.getTargetDate().toString()));
        return toResponse(action);
    }

    // ---------------------------------------------------------------- update

    @Transactional
    public ActionResponse update(long id, ActionUpdateRequest request, AuthPrincipal principal) {
        ActionEntity action = require(id);
        boolean senior = principal.getRoles().stream().anyMatch(SENIOR::contains);
        boolean manager = principal.getRoles().stream().anyMatch(MANAGERS::contains);

        if (!senior) {
            throw new ForbiddenException("Only supervisors and quality managers may update corrective actions");
        }

        boolean ownerChanged = false;
        if (request.ownerId() != null && !request.ownerId().equals(action.getOwnerId())) {
            if (!manager) {
                throw new ForbiddenException("Only quality managers may reassign corrective actions");
            }
            UserEntity owner = userRepository.findById(request.ownerId())
                    .orElseThrow(() -> new NotFoundException("Owner user not found"));
            if (!owner.isEnabled()) {
                throw new BadRequestException("The selected owner is disabled");
            }
            action.setOwnerId(owner.getId());
            ownerChanged = true;
        }
        if (request.targetDate() != null) {
            if (!manager) {
                throw new ForbiddenException("Only quality managers may change target dates");
            }
            action.setTargetDate(request.targetDate());
        }
        if (request.workNotes() != null) {
            action.setWorkNotes(request.workNotes());
        }
        if (request.completionDetails() != null) {
            action.setCompletionDetails(request.completionDetails());
        }

        boolean statusChanged = request.status() != null && !request.status().equals(action.getStatus());
        if (statusChanged) {
            applyTransition(action, request.status(), request.reason(), request.completionDetails(), principal, manager);
        }

        action = actionRepository.save(action);
        if (statusChanged) {
            auditService.record("ACTION", action.getId(), "ACTION_STATUS_CHANGED",
                    request.reason(),
                    Map.of("code", action.getCode(), "status", action.getStatus()));
        } else if (ownerChanged || request.targetDate() != null || request.workNotes() != null) {
            auditService.record("ACTION", action.getId(), "ACTION_UPDATED", request.reason(), null);
        }
        return toResponse(action);
    }

    private void applyTransition(ActionEntity action, String target, String reason, String completionDetails,
                                 AuthPrincipal principal, boolean manager) {
        String current = action.getStatus();
        Set<String> allowed = ALLOWED_TRANSITIONS.getOrDefault(current, Set.of());
        if (!allowed.contains(target)) {
            throw new ConflictException("Invalid corrective-action transition " + current + " -> " + target);
        }
        if ("CANCELLED".equals(target)) {
            if (!manager) {
                throw new ForbiddenException("Only quality managers may cancel corrective actions");
            }
            if (reason == null || reason.isBlank()) {
                throw new BadRequestException("A reason is required to cancel a corrective action");
            }
            action.setStatusReason(reason.trim());
        }
        if ("COMPLETED".equals(target)) {
            String details = completionDetails != null && !completionDetails.isBlank()
                    ? completionDetails
                    : action.getCompletionDetails();
            if (details == null || details.isBlank()) {
                throw new BadRequestException("completionDetails are required to complete a corrective action");
            }
            action.setCompletionDetails(details);
            action.setCompletedAt(java.time.Instant.now());
        }
        if ("IN_PROGRESS".equals(target) && "AWAITING_REINSPECTION".equals(current)) {
            action.setCompletedAt(null);
        }
        action.setStatus(target);
        if (reason != null && !reason.isBlank()) {
            action.setStatusReason(reason.trim());
        }
    }

    // ---------------------------------------------------------------- follow-up

    @Transactional
    public InspectionResponse createReinspection(long actionId, FollowUpRequest request, AuthPrincipal principal) {
        ActionEntity action = require(actionId);
        if ("COMPLETED".equals(action.getStatus()) || "CANCELLED".equals(action.getStatus())) {
            throw new ConflictException("Reinspection is not allowed for a " + action.getStatus() + " action");
        }
        DefectEntity defect = defectRepository.findById(action.getDefectId())
                .orElseThrow(() -> new IllegalStateException("Action points at a missing defect"));
        if ("OPEN".equals(action.getStatus())) {
            action.setStatus("IN_PROGRESS");
            auditService.record("ACTION", action.getId(), "ACTION_STATUS_CHANGED",
                    "Moved to IN_PROGRESS by reinspection start",
                    Map.of("code", action.getCode(), "status", "IN_PROGRESS"));
            actionRepository.save(action);
        }

        InspectionResponse inspection = inspectionService.createFollowUp(
                action.getId(), defect.getInspectionId(), defect.getBatchId(), defect.getProductId(),
                request.itemReference(), request.evidenceScope(), request.observations(), principal);

        if (!"AWAITING_REINSPECTION".equals(action.getStatus())) {
            action.setStatus("AWAITING_REINSPECTION");
            actionRepository.save(action);
            auditService.record("ACTION", action.getId(), "ACTION_STATUS_CHANGED",
                    "Awaiting follow-up inspection result",
                    Map.of("code", action.getCode(), "status", "AWAITING_REINSPECTION"));
        }
        return inspection;
    }

    // ---------------------------------------------------------------- read

    @Transactional(readOnly = true)
    public PageResponse<ActionResponse> list(String search, Long defectId, Long ownerId, String status,
                                             boolean overdue, int page, int size) {
        var result = actionRepository.search(blankToNull(search), defectId, ownerId, blankToNull(status), overdue,
                LocalDate.now(), PageRequest.of(page, Math.min(size, 100)));
        return PageResponse.from(result.map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public ActionDetailResponse detail(long id) {
        ActionEntity action = require(id);
        DefectEntity defect = defectService.require(action.getDefectId());
        List<InspectionResponse> followUps = inspectionService.listForAction(id);
        return new ActionDetailResponse(
                toResponse(action),
                action.getDescription(),
                action.getWorkNotes(),
                action.getCompletionDetails(),
                action.getStatusReason(),
                defectService.toResponse(defect),
                followUps,
                auditQueryService.history("ACTION", id, 50));
    }

    ActionEntity require(long id) {
        return actionRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Corrective action " + id + " not found"));
    }

    public ActionResponse toResponse(ActionEntity action) {
        String ownerName = userRepository.findById(action.getOwnerId())
                .map(u -> u.getDisplayName()).orElse("#" + action.getOwnerId());
        String defectCode = defectRepository.findById(action.getDefectId())
                .map(DefectEntity::getCode).orElse("?");
        return new ActionResponse(
                action.getId(), action.getCode(), action.getDefectId(), defectCode, action.getTitle(),
                action.getOwnerId(), ownerName, action.getTargetDate(), action.getStatus(), action.isOverdue(),
                action.getCreatedAt(), action.getUpdatedAt());
    }

    private ActionEntity saveWithUniqueCode(ActionEntity action) {
        for (int attempt = 0; attempt < 5; attempt++) {
            action.setCode(String.format("CA-%05d", actionRepository.maxId() + 1 + attempt));
            try {
                return actionRepository.saveAndFlush(action);
            } catch (DataIntegrityViolationException ex) {
                // retry with next number
            }
        }
        throw new ConflictException("Could not allocate a unique action code; please retry");
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase(java.util.Locale.ROOT);
    }
}
