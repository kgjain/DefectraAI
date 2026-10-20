package com.qualityinspect.backend.common.idempotency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qualityinspect.backend.common.error.ApiException.ConflictException;
import com.qualityinspect.backend.security.AuthPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.function.Supplier;

/**
 * Server-side duplicate-submission protection:
 * a repeated (user, Idempotency-Key) pair replays the first stored response;
 * the same key with a different payload is rejected with 409.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final IdempotencyRecordRepository repository;
    private final ObjectMapper objectMapper;

    @Transactional
    public <T> T execute(String key, String endpoint, Object requestPayload, Class<T> responseType, Supplier<T> work) {
        if (key == null || key.isBlank()) {
            return work.get();
        }
        Long userId = currentUserId();
        String hash = hash(requestPayload);

        var existing = repository.findByUserIdAndIdemKey(userId, key);
        if (existing.isPresent()) {
            IdempotencyRecord record = existing.get();
            if (!record.getRequestHash().equals(hash)) {
                throw new ConflictException("Idempotency-Key was already used with a different payload");
            }
            if (record.getResponseStatus() == null) {
                throw new ConflictException("An identical request is already being processed");
            }
            log.info("Replaying stored response for idempotency key (user={}, endpoint={})", userId, endpoint);
            try {
                return objectMapper.readValue(record.getResponseBody(), responseType);
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("Could not deserialize stored idempotent response", e);
            }
        }

        IdempotencyRecord record = new IdempotencyRecord();
        record.setUserId(userId);
        record.setIdemKey(key);
        record.setEndpoint(endpoint);
        record.setRequestHash(hash);
        try {
            repository.saveAndFlush(record);
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("An identical request is already being processed");
        }

        try {
            T result = work.get();
            record.setResponseStatus(201);
            try {
                record.setResponseBody(objectMapper.writeValueAsString(result));
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("Could not serialize idempotent response", e);
            }
            repository.save(record);
            return result;
        } catch (RuntimeException ex) {
            repository.delete(record);
            throw ex;
        }
    }

    private String hash(Object payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(json.getBytes(StandardCharsets.UTF_8)));
        } catch (JsonProcessingException | java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("Could not fingerprint request payload", e);
        }
    }

    private Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthPrincipal principal) {
            return principal.getId();
        }
        throw new IllegalStateException("Idempotency requires an authenticated caller");
    }
}
