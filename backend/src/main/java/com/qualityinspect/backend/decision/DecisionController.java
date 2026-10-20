package com.qualityinspect.backend.decision;

import com.qualityinspect.backend.decision.DecisionDtos.*;
import com.qualityinspect.backend.security.AuthPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class DecisionController {

    private final DecisionService decisionService;

    @PostMapping("/api/v1/batches/{id}/decisions")
    @PreAuthorize("hasRole('QUALITY_MANAGER')")
    public ResponseEntity<BatchDecisionResponse> decide(
            @PathVariable long id,
            @Valid @RequestBody BatchDecisionRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(decisionService.decide(id, request, principal, idempotencyKey));
    }

    @GetMapping("/api/v1/batches/{id}/decisions")
    public List<BatchDecisionResponse> history(@PathVariable long id) {
        return decisionService.history(id);
    }
}
