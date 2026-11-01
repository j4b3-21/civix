package com.civix.audit.service;

import java.time.Clock;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.civix.audit.domain.AuditCategory;
import com.civix.audit.domain.AuditEvent;
import com.civix.audit.dto.AuditEventDto;
import com.civix.audit.repository.AuditEventRepository;
import com.civix.common.crypto.Hashing;
import com.civix.common.web.ClientIp;
import com.civix.common.web.RequestCorrelation;
import com.civix.config.CivixProperties;

/**
 * Writes the audit trail. {@link #record} joins the caller's transaction (the event commits or rolls back with
 * the action it describes). {@link #recordIndependently} commits on its own, for denials/failures whose business
 * transaction is rolled back.
 */
@Service
public class AuditService {

    private final AuditEventRepository events;
    private final String ipPepper;
    private final Clock clock;

    public AuditService(AuditEventRepository events, CivixProperties props, Clock clock) {
        this.events = events;
        this.ipPepper = props.audit().ipPepper();
        this.clock = clock;
    }

    @Transactional
    public void record(AuditEntry entry) {
        events.save(toEvent(entry));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordIndependently(AuditEntry entry) {
        events.save(toEvent(entry));
    }

    @Transactional(readOnly = true)
    public Page<AuditEventDto> forUser(UUID userId, AuditCategory category, Pageable pageable) {
        Page<AuditEvent> page = category == null ? events.findByUserId(userId, pageable)
                : events.findByUserIdAndCategory(userId, category, pageable);
        return page.map(AuditEventDto::from);
    }

    private AuditEvent toEvent(AuditEntry e) {
        String ipHash = ClientIp.current().map(ip -> Hashing.sha256Hex(ip + ipPepper)).orElse(null);
        String summary = e.summary == null ? e.action : truncate(e.summary, 500);
        return new AuditEvent(e.userId, e.category, truncate(e.action, 64), e.actorType, truncate(e.actorId, 64),
                truncate(e.resourceType, 32), truncate(e.resourceId, 64), e.outcome, summary,
                truncate(RequestCorrelation.currentIdOrNull(), 64), ipHash, new java.util.LinkedHashMap<>(e.metadata),
                clock.instant());
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
