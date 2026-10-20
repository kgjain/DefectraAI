package com.qualityinspect.backend.inspection;

import com.qualityinspect.backend.batch.BatchEntity;
import com.qualityinspect.backend.batch.BatchRepository;
import com.qualityinspect.backend.common.audit.AuditService;
import com.qualityinspect.backend.common.error.ApiException.*;
import com.qualityinspect.backend.common.idempotency.IdempotencyService;
import com.qualityinspect.backend.common.storage.FileStorageService;
import com.qualityinspect.backend.common.web.PageResponse;
import com.qualityinspect.backend.inspection.InspectionDtos.*;
import com.qualityinspect.backend.ml.ModelResultEntity;
import com.qualityinspect.backend.ml.ModelResultRepository;
import com.qualityinspect.backend.ml.ProcessingAttemptEntity;
import com.qualityinspect.backend.ml.ProcessingAttemptRepository;
import com.qualityinspect.backend.product.ProductRepository;
import com.qualityinspect.backend.security.AuthPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class InspectionService {

    private final InspectionRepository inspectionRepository;
    private final InspectionImageRepository imageRepository;
    private final InspectionReviewRepository reviewRepository;
    private final ModelResultRepository modelResultRepository;
    private final ProcessingAttemptRepository attemptRepository;
    private final BatchRepository batchRepository;
    private final ProductRepository productRepository;
    private final com.qualityinspect.backend.user.UserRepository userRepository;
    private final FileStorageService fileStorage;
    private final AuditService auditService;
    private final IdempotencyService idempotencyService;

    // ---------------------------------------------------------------- listing

    @Transactional(readOnly = true)
    public PageResponse<InspectionResponse> list(Long batchId, Long productId, String status, Long inspectorId,
                                                 String itemReference, String reviewState, String aiState,
                                                 Instant from, Instant to, int page, int size) {
        Page<InspectionEntity> result = inspectionRepository.search(
                batchId, productId, blankToNull(status), inspectorId,
                blankToNull(itemReference == null ? null : itemReference.toUpperCase(Locale.ROOT)),
                blankToNull(reviewState), blankToNull(aiState),
                from, to, PageRequest.of(page, Math.min(size, 100)));

        List<InspectionEntity> items = result.getContent();
        if (items.isEmpty()) {
            return PageResponse.from(result.map(e -> toResponse(e, Map.of(), Map.of(), Map.of(), Set.of(), Map.of())));
        }

        List<Long> inspectionIds = items.stream().map(InspectionEntity::getId).toList();
        List<InspectionImageEntity> images = imageRepository.findByInspectionIdInAndCurrentTrue(inspectionIds);
        List<Long> imageIds = images.stream().map(InspectionImageEntity::getId).toList();
        List<ProcessingAttemptEntity> attempts = imageIds.isEmpty() ? List.of() : attemptRepository.findByImageIdIn(imageIds);
        List<ModelResultEntity> results = imageIds.isEmpty() ? List.of() : modelResultRepository.findByImageIdInAndCurrentTrue(imageIds);
        List<InspectionReviewEntity> reviews = reviewRepository.findByInspectionIdIn(inspectionIds);

        Map<Long, List<InspectionImageEntity>> imagesByInspection = images.stream()
                .collect(Collectors.groupingBy(InspectionImageEntity::getInspectionId));
        Map<Long, List<ProcessingAttemptEntity>> attemptsByImage = attempts.stream()
                .collect(Collectors.groupingBy(ProcessingAttemptEntity::getImageId));
        Map<Long, List<ModelResultEntity>> resultsByImage = results.stream()
                .collect(Collectors.groupingBy(ModelResultEntity::getImageId));
        Set<Long> reviewedInspections = reviews.stream()
                .map(InspectionReviewEntity::getInspectionId).collect(Collectors.toSet());

        Map<Long, String> batchCodes = batchRepository.findAllById(
                        items.stream().map(InspectionEntity::getBatchId).distinct().toList()).stream()
                .collect(Collectors.toMap(BatchEntity::getId, BatchEntity::getCode));
        Map<Long, String> productNames = productRepository.findAllById(
                        items.stream().map(InspectionEntity::getProductId).distinct().toList()).stream()
                .collect(Collectors.toMap(c -> c.getId(), c -> c.getName()));
        Map<Long, String> inspectorNames = usernamesById(
                items.stream().map(InspectionEntity::getInspectorId).filter(Objects::nonNull).distinct().toList());

        return PageResponse.from(result.map(insp -> toResponse(insp, batchCodes, productNames, inspectorNames,
                reviewedInspections.contains(insp.getId())
                        ? Set.of("REVIEWED") : Set.of(),
                imagesByInspection, attemptsByImage, resultsByImage)));
    }

    // ---------------------------------------------------------------- reading

    /** Follow-up inspections linked to one corrective action (for the action detail screen). */
    @Transactional(readOnly = true)
    public List<InspectionResponse> listForAction(long actionId) {
        List<InspectionEntity> items = inspectionRepository.findByCorrectiveActionId(actionId);
        if (items.isEmpty()) {
            return List.of();
        }
        List<Long> ids = items.stream().map(InspectionEntity::getId).toList();
        List<InspectionImageEntity> images = imageRepository.findByInspectionIdInAndCurrentTrue(ids);
        List<Long> imageIds = images.stream().map(InspectionImageEntity::getId).toList();
        List<ProcessingAttemptEntity> attempts = imageIds.isEmpty() ? List.of() : attemptRepository.findByImageIdIn(imageIds);
        List<ModelResultEntity> results = imageIds.isEmpty() ? List.of() : modelResultRepository.findByImageIdInAndCurrentTrue(imageIds);
        Set<Long> reviewed = reviewRepository.findByInspectionIdIn(ids).stream()
                .map(InspectionReviewEntity::getInspectionId).collect(Collectors.toSet());

        Map<Long, List<InspectionImageEntity>> imagesByInspection =
                images.stream().collect(Collectors.groupingBy(InspectionImageEntity::getInspectionId));
        Map<Long, List<ProcessingAttemptEntity>> attemptsByImage =
                attempts.stream().collect(Collectors.groupingBy(ProcessingAttemptEntity::getImageId));
        Map<Long, List<ModelResultEntity>> resultsByImage =
                results.stream().collect(Collectors.groupingBy(ModelResultEntity::getImageId));
        Map<Long, String> batchCodes = batchRepository.findAllById(
                        items.stream().map(InspectionEntity::getBatchId).distinct().toList()).stream()
                .collect(Collectors.toMap(BatchEntity::getId, BatchEntity::getCode));
        Map<Long, String> productNames = productRepository.findAllById(
                        items.stream().map(InspectionEntity::getProductId).distinct().toList()).stream()
                .collect(Collectors.toMap(c -> c.getId(), c -> c.getName()));
        Map<Long, String> inspectorNames = usernamesById(
                items.stream().map(InspectionEntity::getInspectorId).filter(Objects::nonNull).distinct().toList());

        return items.stream()
                .map(insp -> toResponse(insp, batchCodes, productNames, inspectorNames,
                        reviewed.contains(insp.getId()) ? Set.of("REVIEWED") : Set.of(),
                        imagesByInspection, attemptsByImage, resultsByImage))
                .toList();
    }

    @Transactional(readOnly = true)
    public InspectionDetailResponse get(long id) {
        InspectionEntity inspection = require(id);
        List<InspectionImageEntity> images = imageRepository.findByInspectionIdOrderByVersionAsc(id);
        List<Long> imageIds = images.stream().map(InspectionImageEntity::getId).toList();
        List<ProcessingAttemptEntity> attempts = imageIds.isEmpty() ? List.of()
                : attemptRepository.findByImageIdIn(imageIds).stream()
                        .sorted(Comparator.comparing(ProcessingAttemptEntity::getAttemptNo)).toList();
        List<ModelResultEntity> results = imageIds.isEmpty() ? List.of()
                : modelResultRepository.findByImageIdInAndCurrentTrue(imageIds);
        List<InspectionReviewEntity> reviews = reviewRepository.findByInspectionIdOrderByCreatedAtAsc(id);

        Map<Long, String> batchCodes = Map.of(inspection.getBatchId(),
                batchRepository.findById(inspection.getBatchId()).map(BatchEntity::getCode).orElse("?"));
        Map<Long, String> productNames = Map.of(inspection.getProductId(),
                productRepository.findById(inspection.getProductId()).map(p -> p.getName()).orElse("?"));
        Map<Long, String> inspectorNames = usernamesById(
                inspection.getInspectorId() == null ? List.of() : List.of(inspection.getInspectorId()));
        boolean reviewed = reviewRepository.existsByInspectionId(id);

        InspectionResponse head = toResponse(inspection, batchCodes, productNames, inspectorNames,
                reviewed ? Set.of("REVIEWED") : Set.of(),
                images.stream().filter(InspectionImageEntity::isCurrent)
                        .collect(Collectors.groupingBy(InspectionImageEntity::getInspectionId)),
                attempts.stream().collect(Collectors.groupingBy(ProcessingAttemptEntity::getImageId)),
                results.stream().collect(Collectors.groupingBy(ModelResultEntity::getImageId)));

        return new InspectionDetailResponse(
                head,
                images.stream().map(this::toImageResponse).toList(),
                results.stream().sorted(Comparator.comparing(ModelResultEntity::getProcessedAt).reversed())
                        .map(this::toResultResponse).toList(),
                attempts.stream().map(this::toAttemptResponse).toList(),
                reviews.stream().map(this::toReviewResponse).toList());
    }

    // ---------------------------------------------------------------- creating

    @Transactional
    public InspectionResponse create(InspectionCreateRequest request, AuthPrincipal principal, String idempotencyKey) {
        String key = idempotencyKey != null && !idempotencyKey.isBlank()
                ? idempotencyKey : request.idempotencyKey();
        return idempotencyService.execute(key, "POST /inspections", request, InspectionResponse.class,
                () -> doCreate(request, principal, key));
    }

    private InspectionResponse doCreate(InspectionCreateRequest request, AuthPrincipal principal, String key) {
        BatchEntity batch = batchRepository.findById(request.batchId())
                .orElseThrow(() -> new NotFoundException("Batch " + request.batchId() + " not found"));
        if ("ARCHIVED".equals(batch.getStatus())) {
            throw new ConflictException("Cannot create a new inspection on an archived batch");
        }

        InspectionEntity inspection = new InspectionEntity();
        inspection.setBatchId(batch.getId());
        inspection.setProductId(batch.getProductId());
        inspection.setItemReference(request.itemReference().trim());
        inspection.setEvidenceScope(request.evidenceScope());
        inspection.setObservations(request.observations());
        inspection.setInspectorId(principal.getId());
        inspection.setIdempotencyKey(key);
        try {
            inspection = inspectionRepository.saveAndFlush(inspection);
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("An initial inspection for item '"
                    + request.itemReference().trim().toUpperCase() + "' already exists in this batch");
        }

        auditService.record("INSPECTION", inspection.getId(), "INSPECTION_CREATED", null,
                Map.of("batchId", batch.getId(), "itemReference", inspection.getItemReference()));
        return get(inspection.getId()).inspection();
    }

    @Transactional
    public InspectionResponse update(long id, InspectionUpdateRequest request) {
        InspectionEntity inspection = requireEditableDraft(id);
        if (request.itemReference() != null && !request.itemReference().isBlank()) {
            inspection.setItemReference(request.itemReference().trim());
        }
        if (request.evidenceScope() != null && !request.evidenceScope().isBlank()) {
            inspection.setEvidenceScope(request.evidenceScope());
        }
        if (request.observations() != null) {
            inspection.setObservations(request.observations());
        }
        try {
            inspection = inspectionRepository.saveAndFlush(inspection);
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("Another inspection already uses this item reference in this batch");
        }
        auditService.record("INSPECTION", inspection.getId(), "INSPECTION_UPDATED", null, null);
        return get(inspection.getId()).inspection();
    }

    // ---------------------------------------------------------------- follow-up

    /**
     * Creates a FOLLOW_UP inspection linked to a corrective action and the original
     * inspection (parent). Follow-ups are exempt from the initial-item unique index
     * so re-inspections of the same item keep accumulating history.
     */
    @Transactional
    public InspectionResponse createFollowUp(long correctiveActionId, Long parentInspectionId,
                                             long batchId, long productId,
                                             String itemReference, String evidenceScope,
                                             String observations, AuthPrincipal principal) {
        BatchEntity batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new NotFoundException("Batch " + batchId + " not found"));

        InspectionEntity inspection = new InspectionEntity();
        inspection.setBatchId(batch.getId());
        inspection.setProductId(productId);
        inspection.setItemReference(itemReference.trim());
        inspection.setEvidenceScope(evidenceScope == null || evidenceScope.isBlank() ? "ITEM" : evidenceScope);
        inspection.setObservations(observations);
        inspection.setInspectorId(principal.getId());
        inspection.setInspectionType("FOLLOW_UP");
        inspection.setCorrectiveActionId(correctiveActionId);
        inspection.setParentInspectionId(parentInspectionId);
        inspection = inspectionRepository.save(inspection);

        auditService.record("INSPECTION", inspection.getId(), "FOLLOW_UP_CREATED", null,
                Map.of("correctiveActionId", correctiveActionId,
                        "parentInspectionId", parentInspectionId == null ? "" : parentInspectionId,
                        "batchId", batch.getId()));
        return get(inspection.getId()).inspection();
    }

    @Transactional
    public InspectionResponse submit(long id) {        InspectionEntity inspection = requireEditableDraft(id);
        long imageCount = imageRepository.countByInspectionIdAndCurrentTrue(id);
        if (imageCount == 0) {
            throw new UnprocessableEntityException("A submitted inspection needs at least one evidence image");
        }
        inspection.setStatus("SUBMITTED");
        inspection.setSubmittedAt(Instant.now());
        inspection = inspectionRepository.save(inspection);
        auditService.record("INSPECTION", inspection.getId(), "INSPECTION_SUBMITTED", null,
                Map.of("images", imageCount));
        return get(inspection.getId()).inspection();
    }

    @Transactional
    public void delete(long id) {
        InspectionEntity inspection = require(id);
        if (!"DRAFT".equals(inspection.getStatus())) {
            throw new ConflictException("Only draft inspections can be deleted; submitted evidence is retained");
        }
        for (InspectionImageEntity image : imageRepository.findByInspectionIdOrderByVersionAsc(id)) {
            fileStorage.delete(image.getStorageKey());
        }
        imageRepository.deleteByInspectionId(id);
        inspectionRepository.delete(inspection);
        auditService.record("INSPECTION", id, "INSPECTION_DRAFT_DELETED",
                "Draft removed before submission", null);
    }

    // ---------------------------------------------------------------- images

    @Transactional
    public ImageResponse uploadImage(long inspectionId, MultipartFile file, AuthPrincipal principal) {
        InspectionEntity inspection = requireUploadable(inspectionId);
        FileStorageService.StoredFile stored = fileStorage.store(file);

        InspectionImageEntity image = new InspectionImageEntity();
        image.setInspectionId(inspection.getId());
        image.setStorageKey(stored.storageKey());
        image.setOriginalFilename(sanitizeFilename(file.getOriginalFilename()));
        image.setContentType(stored.contentType());
        image.setSizeBytes(stored.sizeBytes());
        image.setSha256(stored.sha256());
        image.setUploadedBy(principal.getId());
        image = imageRepository.save(image);

        auditService.record("INSPECTION", inspectionId, "IMAGE_UPLOADED", null,
                Map.of("imageId", image.getId(), "sizeBytes", image.getSizeBytes(),
                        "sha256", image.getSha256()));
        return toImageResponse(image);
    }

    @Transactional
    public ImageResponse replaceImage(long imageId, MultipartFile file, AuthPrincipal principal, String reason) {
        InspectionImageEntity old = imageRepository.findById(imageId)
                .orElseThrow(() -> new NotFoundException("Image " + imageId + " not found"));
        InspectionEntity inspection = requireUploadable(old.getInspectionId());
        if (!old.isCurrent()) {
            throw new ConflictException("Only the current image version can be replaced");
        }
        FileStorageService.StoredFile stored = fileStorage.store(file);

        InspectionImageEntity replacement = new InspectionImageEntity();
        replacement.setInspectionId(inspection.getId());
        replacement.setStorageKey(stored.storageKey());
        replacement.setOriginalFilename(sanitizeFilename(file.getOriginalFilename()));
        replacement.setContentType(stored.contentType());
        replacement.setSizeBytes(stored.sizeBytes());
        replacement.setSha256(stored.sha256());
        replacement.setVersion(old.getVersion() + 1);
        replacement.setUploadedBy(principal.getId());
        replacement = imageRepository.save(replacement);

        old.setCurrent(false);
        old.setReplacedById(replacement.getId());
        imageRepository.save(old);

        auditService.record("INSPECTION", inspection.getId(), "IMAGE_REPLACED",
                reason == null || reason.isBlank() ? "Evidence replaced" : reason,
                Map.of("previousImageId", old.getId(), "imageId", replacement.getId(),
                        "previousVersion", old.getVersion(), "version", replacement.getVersion()));
        return toImageResponse(replacement);
    }

    @Transactional
    public void deleteImage(long imageId) {
        InspectionImageEntity image = imageRepository.findById(imageId)
                .orElseThrow(() -> new NotFoundException("Image " + imageId + " not found"));
        InspectionEntity inspection = require(image.getInspectionId());
        if (!"DRAFT".equals(inspection.getStatus())) {
            throw new ConflictException("Only draft evidence can be removed; submitted images are retained");
        }
        if (!image.isCurrent()) {
            throw new ConflictException("Historic image versions are retained");
        }
        fileStorage.delete(image.getStorageKey());
        imageRepository.delete(image);
        auditService.record("INSPECTION", inspection.getId(), "IMAGE_REMOVED",
                "Draft evidence removed before submission",
                Map.of("imageId", imageId));
    }

    @Transactional(readOnly = true)
    public ImagePayload readImage(long imageId) {
        InspectionImageEntity image = imageRepository.findById(imageId)
                .orElseThrow(() -> new NotFoundException("Image " + imageId + " not found"));
        return new ImagePayload(image.getContentType(), image.getOriginalFilename(),
                fileStorage.read(image.getStorageKey()));
    }

    public record ImagePayload(String contentType, String filename, byte[] bytes) {}

    // ---------------------------------------------------------------- helpers

    public InspectionEntity require(long id) {
        return inspectionRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Inspection " + id + " not found"));
    }

    private InspectionEntity requireEditableDraft(long id) {
        InspectionEntity inspection = require(id);
        if (!"DRAFT".equals(inspection.getStatus())) {
            throw new ConflictException("Only draft inspections can be modified");
        }
        return inspection;
    }

    private InspectionEntity requireUploadable(long id) {
        InspectionEntity inspection = require(id);
        if ("CANCELLED".equals(inspection.getStatus())) {
            throw new ConflictException("Cancelled inspections accept no further evidence");
        }
        return inspection;
    }

    private InspectionResponse toResponse(InspectionEntity inspection,
                                          Map<Long, String> batchCodes,
                                          Map<Long, String> productNames,
                                          Map<Long, String> inspectorNames,
                                          Set<String> reviewFlags,
                                          Map<Long, List<InspectionImageEntity>> imagesByInspection) {
        return toResponse(inspection, batchCodes, productNames, inspectorNames, reviewFlags,
                imagesByInspection, Map.of(), Map.of());
    }

    private InspectionResponse toResponse(InspectionEntity inspection,
                                          Map<Long, String> batchCodes,
                                          Map<Long, String> productNames,
                                          Map<Long, String> inspectorNames,
                                          Set<String> reviewFlags,
                                          Map<Long, List<InspectionImageEntity>> imagesByInspection,
                                          Map<Long, List<ProcessingAttemptEntity>> attemptsByImage,
                                          Map<Long, List<ModelResultEntity>> resultsByImage) {
        List<InspectionImageEntity> images =
                imagesByInspection.getOrDefault(inspection.getId(), List.of());
        String aiState = computeAiState(images, attemptsByImage, resultsByImage);
        String reviewState = reviewFlags.isEmpty() ? "PENDING" : reviewFlags.iterator().next();

        return new InspectionResponse(
                inspection.getId(),
                inspection.getBatchId(),
                batchCodes.getOrDefault(inspection.getBatchId(), "?"),
                inspection.getProductId(),
                productNames.getOrDefault(inspection.getProductId(), "?"),
                inspection.getItemReference(),
                inspection.getEvidenceScope(),
                inspection.getInspectionType(),
                inspection.getStatus(),
                inspection.getObservations(),
                inspection.getInspectorId() == null ? null
                        : inspectorNames.getOrDefault(inspection.getInspectorId(), "#" + inspection.getInspectorId()),
                inspection.getCorrectiveActionId(),
                aiState,
                reviewState,
                inspection.getSubmittedAt(),
                inspection.getCreatedAt());
    }

    /** Waiting / Processing / Completed / Failed — computed from persisted attempts and results only. */
    public static String computeAiState(List<InspectionImageEntity> images,
                                 Map<Long, List<ProcessingAttemptEntity>> attemptsByImage,
                                 Map<Long, List<ModelResultEntity>> resultsByImage) {
        if (images.isEmpty()) {
            return "PENDING";
        }
        List<ProcessingAttemptEntity> attempts = new ArrayList<>();
        List<ModelResultEntity> results = new ArrayList<>();
        for (InspectionImageEntity image : images) {
            attempts.addAll(attemptsByImage.getOrDefault(image.getId(), List.of()));
            results.addAll(resultsByImage.getOrDefault(image.getId(), List.of()));
        }
        boolean processing = attempts.stream().anyMatch(a -> "PENDING".equals(a.getStatus()) || "PROCESSING".equals(a.getStatus()));
        if (processing) {
            return "PROCESSING";
        }
        if (!results.isEmpty()) {
            return "COMPLETED";
        }
        boolean failed = attempts.stream().anyMatch(a -> "FAILED".equals(a.getStatus()));
        if (failed) {
            return "FAILED";
        }
        return "PENDING";
    }

    private ImageResponse toImageResponse(InspectionImageEntity image) {
        return new ImageResponse(
                image.getId(), image.getInspectionId(), image.getOriginalFilename(), image.getContentType(),
                image.getSizeBytes(), image.getSha256(), image.getVersion(), image.isCurrent(),
                image.getReplacedById(), "/api/v1/images/" + image.getId(), image.getCreatedAt());
    }

    public ModelResultResponse toResultResponse(ModelResultEntity result) {
        return new ModelResultResponse(
                result.getId(), result.getImageId(), result.getAttemptId(), result.getPredictedClass(),
                result.getScore(), result.getScoreType(), result.getModelName(), result.getModelVersion(),
                result.getProcessedAt(), result.getDurationMs(), result.isCurrent());
    }

    public ProcessingAttemptResponse toAttemptResponse(ProcessingAttemptEntity attempt) {
        return new ProcessingAttemptResponse(
                attempt.getId(), attempt.getImageId(), attempt.getAttemptNo(), attempt.getStatus(),
                attempt.getErrorDetails(), attempt.getDurationMs(), attempt.getModelName(), attempt.getModelVersion(),
                attempt.getStartedAt(), attempt.getFinishedAt());
    }

    public ReviewResponse toReviewResponse(InspectionReviewEntity review) {
        String reviewer = userRepository.findById(review.getReviewerId())
                .map(u -> u.getUsername()).orElse("#" + review.getReviewerId());
        String roles = userRepository.findById(review.getReviewerId())
                .map(u -> String.join(",", u.roleCodes())).orElse(null);
        return new ReviewResponse(
                review.getId(), review.getInspectionId(), review.getModelResultId(), reviewer,
                review.getDecision(), review.getConfirmedOutcome(), review.getReason(),
                review.getEvidenceRequestedNote(), roles, review.getCreatedAt());
    }

    private Map<Long, String> usernamesById(List<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(u -> u.getId(), u -> u.getUsername()));
    }

    private String sanitizeFilename(String name) {
        if (name == null || name.isBlank()) {
            return "image";
        }
        String cleaned = name.replaceAll("[\\\\/:*?\"<>|\\r\\n]", "_");
        return cleaned.length() > 255 ? cleaned.substring(cleaned.length() - 255) : cleaned;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase(Locale.ROOT);
    }
}
