package com.qualityinspect.backend.ml;

import com.qualityinspect.backend.common.audit.AuditService;
import com.qualityinspect.backend.common.storage.FileStorageService;
import com.qualityinspect.backend.inspection.InspectionImageEntity;
import com.qualityinspect.backend.inspection.InspectionImageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Executes ML attempts asynchronously. Every outcome (success or failure) is
 * persisted; failures stay retryable and never delete the inspection evidence.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MlProcessor {

    private final ProcessingAttemptRepository attemptRepository;
    private final ModelResultRepository modelResultRepository;
    private final InspectionImageRepository imageRepository;
    private final FileStorageService fileStorage;
    private final MlServiceClient mlClient;
    private final AuditService auditService;

    @Async
    @Transactional
    public void process(Long attemptId, Long imageId, Long requestedBy) {
        ProcessingAttemptEntity attempt = attemptRepository.findById(attemptId).orElse(null);
        InspectionImageEntity image = imageRepository.findById(imageId).orElse(null);
        if (attempt == null || image == null) {
            log.warn("ML attempt {} or image {} vanished before processing", attemptId, imageId);
            return;
        }

        attempt.setStatus("PROCESSING");
        attempt.setStartedAt(Instant.now());
        attemptRepository.save(attempt);

        Instant start = Instant.now();
        try {
            byte[] bytes = fileStorage.read(image.getStorageKey());
            MlPredictResponse prediction = mlClient.predict(bytes, image.getOriginalFilename(), image.getContentType());

            // a re-run supersedes the previous current result but keeps its history
            for (ModelResultEntity previous : modelResultRepository.findByImageIdOrderByProcessedAtDesc(imageId)) {
                if (previous.isCurrent()) {
                    previous.setCurrent(false);
                    modelResultRepository.save(previous);
                }
            }

            ModelResultEntity result = new ModelResultEntity();
            result.setImageId(imageId);
            result.setAttemptId(attempt.getId());
            result.setPredictedClass(prediction.predictedClass());
            result.setScore(prediction.score());
            result.setScoreType(prediction.scoreType());
            result.setModelName(prediction.modelName());
            result.setModelVersion(prediction.modelVersion());
            result.setDurationMs(prediction.durationMs() == null
                    ? (int) Duration.between(start, Instant.now()).toMillis()
                    : prediction.durationMs());
            modelResultRepository.save(result);

            attempt.setStatus("COMPLETED");
            attempt.setDurationMs(result.getDurationMs());
            attempt.setModelName(result.getModelName());
            attempt.setModelVersion(result.getModelVersion());
            attempt.setFinishedAt(Instant.now());
            attemptRepository.save(attempt);

            auditService.record("IMAGE", imageId, "ML_PROCESSING_COMPLETED", null,
                    Map.of("attemptId", attempt.getId(),
                            "predictedClass", result.getPredictedClass(),
                            "modelVersion", result.getModelVersion()));
        } catch (Exception ex) {
            log.warn("ML processing failed for image {} attempt {}: {}", imageId, attemptId, ex.getMessage());
            attempt.setStatus("FAILED");
            attempt.setErrorDetails(truncate(ex.getMessage() == null ? ex.toString() : ex.getMessage()));
            attempt.setFinishedAt(Instant.now());
            attempt.setDurationMs((int) Duration.between(start, Instant.now()).toMillis());
            attemptRepository.save(attempt);
            auditService.record("IMAGE", imageId, "ML_PROCESSING_FAILED", null,
                    Map.of("attemptId", attempt.getId(), "error", attempt.getErrorDetails()));
        }
    }

    private String truncate(String value) {
        String v = value.replaceAll("(?i)(api[_-]?key|token|password)=[^&\\s]+", "$1=***");
        return v.length() > 1000 ? v.substring(0, 1000) : v;
    }
}
