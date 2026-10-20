package com.qualityinspect.backend.audit;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qualityinspect.backend.common.audit.AuditEventEntity;
import com.qualityinspect.backend.common.audit.AuditEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuditQueryService {

    private final AuditEventRepository repository;
    private final ObjectMapper objectMapper;

    public AuditDtos.AuditEventResponse toResponse(AuditEventEntity event) {
        Map<String, Object> details = null;
        if (event.getDetails() != null && !event.getDetails().isBlank()) {
            try {
                details = objectMapper.readValue(event.getDetails(), new TypeReference<Map<String, Object>>() {});
            } catch (Exception e) {
                details = Map.of("raw", event.getDetails());
            }
        }
        return new AuditDtos.AuditEventResponse(
                event.getId(), event.getEntityType(), event.getEntityId(), event.getAction(),
                event.getActorUsername(), event.getReason(), details, event.getOccurredAt());
    }

    public List<AuditDtos.AuditEventResponse> history(String entityType, Object entityId, int limit) {
        return repository.search(entityType, String.valueOf(entityId), null, null,
                        org.springframework.data.domain.PageRequest.of(0, limit))
                .getContent().stream().map(this::toResponse).toList();
    }
}
