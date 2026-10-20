package com.qualityinspect.backend.common.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qualityinspect.backend.security.AuthPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Map;

/** Writes append-only audit events for every sensitive/state-changing operation. */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditEventRepository repository;
    private final ObjectMapper objectMapper;

    public void record(String entityType, Object entityId, String action, String reason, Map<String, Object> details) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Long actorId = null;
        String actorName = null;
        if (auth != null && auth.getPrincipal() instanceof AuthPrincipal principal) {
            actorId = principal.getId();
            actorName = principal.getUsername();
        }

        AuditEventEntity event = new AuditEventEntity();
        event.setEntityType(entityType);
        event.setEntityId(String.valueOf(entityId));
        event.setAction(action);
        event.setActorId(actorId);
        event.setActorUsername(actorName);
        event.setReason(reason);
        if (details != null && !details.isEmpty()) {
            try {
                event.setDetails(objectMapper.writeValueAsString(details));
            } catch (JsonProcessingException e) {
                event.setDetails("{\"error\":\"details-not-serializable\"}");
            }
        }
        repository.save(event);
    }

    public void record(String entityType, Object entityId, String action) {
        record(entityType, entityId, action, null, null);
    }

    public void record(String entityType, Object entityId, String action, String reason) {
        record(entityType, entityId, action, reason, null);
    }

    /**
     * Records an event with an explicit actor. Used by login, where no
     * SecurityContext exists yet and the actor must still be captured.
     */
    public void recordAsUser(String entityType, Object entityId, String action,
                             Long actorId, String actorUsername) {
        AuditEventEntity event = new AuditEventEntity();
        event.setEntityType(entityType);
        event.setEntityId(String.valueOf(entityId));
        event.setAction(action);
        event.setActorId(actorId);
        event.setActorUsername(actorUsername);
        repository.save(event);
    }
}
