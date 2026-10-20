package com.qualityinspect.backend.common.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import java.time.Instant;
import java.util.List;

/** Uniform error payload for every non-2xx response. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
        int status,
        String error,
        String message,
        String path,
        Instant timestamp,
        List<FieldViolation> fieldErrors) {

    public record FieldViolation(String field, String message) {}

    public static ApiError of(int status, String error, String message) {
        return new ApiError(status, error, message, currentPath(), Instant.now(), null);
    }

    public static ApiError withFields(int status, String error, String message, List<FieldViolation> fields) {
        return new ApiError(status, error, message, currentPath(), Instant.now(), fields);
    }

    private static String currentPath() {
        try {
            if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
                HttpServletRequest request = attrs.getRequest();
                return request.getRequestURI();
            }
        } catch (Exception ignored) {
            // non-web context (tests) - path omitted
        }
        return null;
    }
}
