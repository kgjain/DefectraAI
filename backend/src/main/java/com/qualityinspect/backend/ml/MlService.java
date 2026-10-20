package com.qualityinspect.backend.ml;

import com.qualityinspect.backend.common.audit.AuditService;
import com.qualityinspect.backend.common.error.ApiException.*;
import com.qualityinspect.backend.inspection.InspectionDtos.ModelResultResponse;
import com.qualityinspect.backend.inspection.InspectionDtos.ProcessingAttemptResponse;
import com.qualityinspect.backend.inspection.InspectionEntity;
import com.qualityinspect.backend.inspection.InspectionImageEntity;
import com.qualityinspect.backend.inspection.InspectionImageRepository;
import com.qualityinspect.backend.inspection.InspectionRepository;
import com.qualityinspect.backend.security.AuthPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MlService {

    private final InspectionImageRepository imageRepository;
    private final InspectionRepository inspectionRepository;
    private final ProcessingAttemptRepository attemptRepository;
    private final ModelResultRepository modelResultRepository;
    private final MlProcessor mlProcessor;
    private final AuditService auditService;
    private final com.qualityinspect.backend.inspection.InspectionService inspectionService;

    @Transactional
    public ProcessingAttemptResponse requestProcessing(long imageId, AuthPrincipal principal) {
        InspectionImageEntity image = imageRepository.findById(imageId)
                .orElseThrow(() -> new NotFoundException("Image " + imageId + " not found"));
        InspectionEntity inspection = inspectionRepository.findById(image.getInspectionId())
                .orElseThrow(() -> new NotFoundException("Inspection not found"));
        if (!"SUBMITTED".equals(inspection.getStatus())) {
            throw new ConflictException("Submit the inspection before requesting AI analysis");
        }
        if (!image.isCurrent()) {
            throw new ConflictException("Only the current evidence version can be processed");
        }
        if (attemptRepository.existsByImageIdAndStatusIn(imageId, List.of("PENDING", "PROCESSING"))) {
            throw new ConflictException("Analysis is already in progress for this image");
        }

        int nextAttempt = attemptRepository.findTopByImageIdOrderByAttemptNoDesc(imageId)
                .map(a -> a.getAttemptNo() + 1).orElse(1);

        ProcessingAttemptEntity attempt = new ProcessingAttemptEntity();
        attempt.setImageId(imageId);
        attempt.setAttemptNo(nextAttempt);
        attempt.setStatus("PENDING");
        attempt.setRequestedBy(principal.getId());
        attempt = attemptRepository.saveAndFlush(attempt);

        auditService.record("IMAGE", imageId, "ML_PROCESS_REQUESTED", null,
                Map.of("attemptId", attempt.getId(), "attemptNo", nextAttempt));

        Long attemptId = attempt.getId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                mlProcessor.process(attemptId, imageId, principal.getId());
            }
        });
        return inspectionService.toAttemptResponse(attempt);
    }

    @Transactional(readOnly = true)
    public List<ModelResultResponse> results(long imageId) {
        if (imageRepository.findById(imageId).isEmpty()) {
            throw new NotFoundException("Image " + imageId + " not found");
        }
        return modelResultRepository.findByImageIdOrderByProcessedAtDesc(imageId).stream()
                .map(inspectionService::toResultResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProcessingAttemptResponse attempt(long id) {
        ProcessingAttemptEntity attempt = attemptRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Processing attempt " + id + " not found"));
        return inspectionService.toAttemptResponse(attempt);
    }
}
