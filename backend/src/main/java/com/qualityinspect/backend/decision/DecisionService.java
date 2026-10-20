package com.qualityinspect.backend.decision;

import com.qualityinspect.backend.batch.BatchEntity;
import com.qualityinspect.backend.batch.BatchRepository;
import com.qualityinspect.backend.common.audit.AuditService;
import com.qualityinspect.backend.common.error.ApiException.*;
import com.qualityinspect.backend.decision.DecisionDtos.*;
import com.qualityinspect.backend.security.AuthPrincipal;
import com.qualityinspect.backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * Batch disposition decisions (HOLD/RELEASE) — separate from individual inspection
 * outcomes. Only quality managers may decide, and a reason is always mandatory.
 * A model prediction or single inspection never releases a batch by itself.
 */
@Service
@RequiredArgsConstructor
public class DecisionService {

    private final BatchDecisionRepository decisionRepository;
    private final BatchRepository batchRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final com.qualityinspect.backend.common.idempotency.IdempotencyService idempotencyService;

    @Transactional
    public BatchDecisionResponse decide(long batchId, BatchDecisionRequest request, AuthPrincipal principal,
                                        String idempotencyKey) {
        return idempotencyService.execute(idempotencyKey,
                "POST /batches/" + batchId + "/decisions",
                Map.of("batchId", batchId, "decision", request.decision(), "reason", request.reason()),
                BatchDecisionResponse.class, () -> doDecide(batchId, request, principal));
    }

    private BatchDecisionResponse doDecide(long batchId, BatchDecisionRequest request, AuthPrincipal principal) {
        BatchEntity batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new NotFoundException("Batch " + batchId + " not found"));
        if (request.reason() == null || request.reason().trim().length() < 5) {
            throw new BadRequestException("A meaningful reason is required for a batch decision");
        }

        BatchDecisionEntity decision = new BatchDecisionEntity();
        decision.setBatchId(batch.getId());
        decision.setDecision(request.decision());
        decision.setReason(request.reason().trim());
        decision.setDecidedBy(principal.getId());
        decision = decisionRepository.save(decision);

        auditService.record("BATCH", batch.getId(), "BATCH_DECISION_" + request.decision(),
                request.reason().trim(),
                Map.of("decisionId", decision.getId(), "decision", request.decision()));
        return toResponse(decision);
    }

    @Transactional(readOnly = true)
    public List<BatchDecisionResponse> history(long batchId) {
        if (batchRepository.findById(batchId).isEmpty()) {
            throw new NotFoundException("Batch " + batchId + " not found");
        }
        return decisionRepository.history(batchId).stream().map(this::toResponse).toList();
    }

    private BatchDecisionResponse toResponse(BatchDecisionEntity decision) {
        String username = userRepository.findById(decision.getDecidedBy())
                .map(u -> u.getUsername()).orElse("#" + decision.getDecidedBy());
        return new BatchDecisionResponse(
                decision.getId(), decision.getBatchId(), decision.getDecision(),
                decision.getReason(), username, decision.getDecidedAt());
    }
}
