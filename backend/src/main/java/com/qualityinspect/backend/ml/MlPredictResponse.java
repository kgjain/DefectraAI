package com.qualityinspect.backend.ml;

import lombok.Builder;

import java.math.BigDecimal;

/** Response contract of the FastAPI ml-service `/predict` endpoint. */
public record MlPredictResponse(
        String predictedClass,
        BigDecimal score,
        String scoreType,
        String modelName,
        String modelVersion,
        Integer durationMs) {

    @Builder
    public MlPredictResponse {}
}
