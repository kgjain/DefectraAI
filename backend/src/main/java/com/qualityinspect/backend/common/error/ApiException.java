package com.qualityinspect.backend.common.error;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/** Base for exceptions with a controlled HTTP status and a safe public message. */
@Getter
public class ApiException extends RuntimeException {

    private final HttpStatus status;

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public ApiException(HttpStatus status, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    public static class NotFoundException extends ApiException {
        public NotFoundException(String message) {
            super(HttpStatus.NOT_FOUND, message);
        }
    }

    public static class BadRequestException extends ApiException {
        public BadRequestException(String message) {
            super(HttpStatus.BAD_REQUEST, message);
        }
    }

    /** Workflow / state conflicts and duplicate records. */
    public static class ConflictException extends ApiException {
        public ConflictException(String message) {
            super(HttpStatus.CONFLICT, message);
        }
    }

    /** Explicit server-side authorization refusal (defense in depth beyond @PreAuthorize). */
    public static class ForbiddenException extends ApiException {
        public ForbiddenException(String message) {
            super(HttpStatus.FORBIDDEN, message);
        }
    }

    /** Upstream AI service unavailable — record is preserved, retry allowed. */
    public static class ServiceUnavailableException extends ApiException {
        public ServiceUnavailableException(String message) {
            super(HttpStatus.SERVICE_UNAVAILABLE, message);
        }
    }

    public static class PayloadTooLargeException extends ApiException {
        public PayloadTooLargeException(String message) {
            super(HttpStatus.PAYLOAD_TOO_LARGE, message);
        }
    }

    public static class UnsupportedMediaTypeException extends ApiException {
        public UnsupportedMediaTypeException(String message) {
            super(HttpStatus.UNSUPPORTED_MEDIA_TYPE, message);
        }
    }

    public static class UnauthorizedException extends ApiException {
        public UnauthorizedException(String message) {
            super(HttpStatus.UNAUTHORIZED, message);
        }
    }

    /** Semantically invalid request (e.g. submitting an inspection with no image). */
    public static class UnprocessableEntityException extends ApiException {
        public UnprocessableEntityException(String message) {
            super(HttpStatus.UNPROCESSABLE_ENTITY, message);
        }
    }
}
