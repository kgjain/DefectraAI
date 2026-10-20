package com.qualityinspect.backend.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qualityinspect.backend.common.error.ApiException.ConflictException;
import com.qualityinspect.backend.common.idempotency.IdempotencyRecord;
import com.qualityinspect.backend.common.idempotency.IdempotencyRecordRepository;
import com.qualityinspect.backend.common.idempotency.IdempotencyService;
import com.qualityinspect.backend.security.AuthPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class IdempotencyServiceTest {

    @Mock IdempotencyRecordRepository repository;
    ObjectMapper mapper = new ObjectMapper();
    IdempotencyService service;

    @BeforeEach
    void setUp() {
        service = new IdempotencyService(repository, mapper);
        var principal = new AuthPrincipal(1L, "tester", "hash", true,
                java.util.Set.of("QUALITY_MANAGER"));
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, java.util.List.of()));
    }

    private String hashOf(Object payload) {
        try {
            var m = IdempotencyService.class.getDeclaredMethod("hash", Object.class);
            m.setAccessible(true);
            return (String) m.invoke(service, payload);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private IdempotencyRecord stored(String requestHash, Integer status, String body) {
        IdempotencyRecord r = new IdempotencyRecord();
        r.setUserId(1L);
        r.setIdemKey("key-1");
        r.setEndpoint("/inspections");
        r.setRequestHash(requestHash);
        r.setResponseStatus(status);
        r.setResponseBody(body);
        return r;
    }

    @Test
    void blankKey_bypassesIdempotency_andRunsWork() {
        var result = service.execute(null, "/inspections", "payload", String.class, () -> "fresh");

        assertEquals("fresh", result);
        verifyNoInteractions(repository);
    }

    @Test
    void firstUse_storesRecord_andReturnsWorkResult() {
        when(repository.findByUserIdAndIdemKey(1L, "key-1")).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var result = service.execute("key-1", "/inspections", "payload", String.class, () -> "created");

        assertEquals("created", result);
        verify(repository).saveAndFlush(any());
        verify(repository).save(any());
    }

    @Test
    void replay_sameKeySamePayload_returnsStoredResponse() {
        when(repository.findByUserIdAndIdemKey(1L, "key-1"))
                .thenReturn(Optional.of(stored(hashOf("payload"), 201, "\"original\"")));

        var result = service.execute("key-1", "/inspections", "payload", String.class, () -> "should-not-run");

        assertEquals("original", result);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void replay_sameKeyDifferentPayload_throwsConflict() {
        when(repository.findByUserIdAndIdemKey(1L, "key-1"))
                .thenReturn(Optional.of(stored("some-other-hash", 201, "{}")));

        assertThrows(ConflictException.class,
                () -> service.execute("key-1", "/inspections", "payload", String.class, () -> "x"));
    }

    @Test
    void inFlightDuplicate_identicalRequest_throwsConflict() {
        when(repository.findByUserIdAndIdemKey(1L, "key-1"))
                .thenReturn(Optional.of(stored(hashOf("payload"), null, null)));

        assertThrows(ConflictException.class,
                () -> service.execute("key-1", "/inspections", "payload", String.class, () -> "x"));
    }

    @Test
    void failedWork_deletesRecord_andRethrows() {
        when(repository.findByUserIdAndIdemKey(1L, "key-1")).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThrows(IllegalStateException.class,
                () -> service.execute("key-1", "/inspections", "payload", String.class,
                        () -> { throw new IllegalStateException("boom"); }));
        verify(repository).delete(any());
    }
}
