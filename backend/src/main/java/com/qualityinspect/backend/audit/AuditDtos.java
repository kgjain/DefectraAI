package com.qualityinspect.backend.audit;

import java.time.Instant;
import java.util.Map;

public final class AuditDtos {

    private AuditDtos() {}

    public record AuditEventResponse(
            long id, String entityType, String entityId, String action,
            String actor, String reason, Map<String, Object> details, Instant occurredAt) {}
}
