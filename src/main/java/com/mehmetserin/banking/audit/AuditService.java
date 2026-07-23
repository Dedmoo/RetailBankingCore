package com.mehmetserin.banking.audit;

import com.mehmetserin.banking.common.RequestIdFilter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AuditService {

    private final AuditEventRepository auditEventRepository;

    public AuditService(AuditEventRepository auditEventRepository) {
        this.auditEventRepository = auditEventRepository;
    }

    @Transactional
    public void record(String eventType, UUID actorUserId, String entityType, UUID entityId, String detail) {
        auditEventRepository.save(new AuditEvent(
                eventType,
                actorUserId,
                entityType,
                entityId.toString(),
                RequestIdFilter.currentRequestId(),
                detail));
    }
}
