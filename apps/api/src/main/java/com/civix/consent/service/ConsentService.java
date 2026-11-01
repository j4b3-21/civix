package com.civix.consent.service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.civix.audit.domain.AuditCategory;
import com.civix.audit.service.AuditEntry;
import com.civix.audit.service.AuditService;
import com.civix.common.api.ApiException;
import com.civix.consent.domain.ConsentPurpose;
import com.civix.consent.domain.ConsentRecord;
import com.civix.consent.dto.ConsentDto;
import com.civix.consent.repository.ConsentRecordRepository;

@Service
public class ConsentService {

    public static final String NOTICE_VERSION = "2026-11-01";

    private final ConsentRecordRepository records;
    private final AuditService audit;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public ConsentService(ConsentRecordRepository records, AuditService audit, ApplicationEventPublisher events, Clock clock) {
        this.records = records;
        this.audit = audit;
        this.events = events;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<ConsentDto> current(UUID userId) {
        List<ConsentRecord> history = records.findByUserIdOrderByCreatedAtDesc(userId);
        Map<ConsentPurpose, ConsentRecord> latest = new EnumMap<>(ConsentPurpose.class);
        Map<ConsentPurpose, Instant> lastGrant = new EnumMap<>(ConsentPurpose.class);
        for (ConsentRecord r : history) {
            latest.putIfAbsent(r.getPurpose(), r);
            if (r.isGranted()) {
                lastGrant.putIfAbsent(r.getPurpose(), r.getCreatedAt());
            }
        }
        List<ConsentDto> result = new ArrayList<>();
        for (ConsentPurpose purpose : ConsentPurpose.values()) {
            result.add(toDto(purpose, latest.get(purpose), lastGrant.get(purpose)));
        }
        return result;
    }

    @Transactional
    public ConsentDto update(UUID userId, ConsentPurpose purpose, boolean granted) {
        Instant now = clock.instant();
        ConsentRecord previous = records.findFirstByUserIdAndPurposeOrderByCreatedAtDesc(userId, purpose).orElse(null);
        Instant lastGrant = previous != null && previous.isGranted() ? previous.getCreatedAt() : null;
        if (previous != null && previous.isGranted() == granted) {
            // Idempotent: no new record when nothing changes.
            if (!granted) {
                lastGrant = lastGrantBefore(userId, purpose);
            }
            return toDto(purpose, previous, lastGrant);
        }
        if (!granted && previous == null) {
            return toDto(purpose, null, null);
        }
        ConsentRecord saved = records.save(new ConsentRecord(userId, purpose, granted, NOTICE_VERSION, now));
        audit.record(AuditEntry.of(AuditCategory.CONSENT, granted ? "CONSENT_GRANTED" : "CONSENT_REVOKED")
                .user(userId).resource("CONSENT", purpose.name())
                .summary((granted ? "Granted" : "Revoked") + " consent for " + purpose.name())
                .meta("purpose", purpose.name()).meta("noticeVersion", NOTICE_VERSION));
        events.publishEvent(new ConsentChangedEvent(userId, purpose, granted));
        return toDto(purpose, saved, granted ? saved.getCreatedAt() : lastGrant);
    }

    @Transactional(readOnly = true)
    public boolean has(UUID userId, ConsentPurpose purpose) {
        return records.findFirstByUserIdAndPurposeOrderByCreatedAtDesc(userId, purpose)
                .map(ConsentRecord::isGranted)
                .orElse(false);
    }

    /** @throws ApiException 403 {@code CONSENT_REQUIRED} */
    @Transactional(readOnly = true)
    public void require(UUID userId, ConsentPurpose purpose) {
        if (!has(userId, purpose)) {
            throw ApiException.consentRequired(purpose.name());
        }
    }

    @Transactional(readOnly = true)
    public Set<ConsentPurpose> granted(UUID userId) {
        Set<ConsentPurpose> granted = EnumSet.noneOf(ConsentPurpose.class);
        Set<ConsentPurpose> seen = EnumSet.noneOf(ConsentPurpose.class);
        for (ConsentRecord r : records.findByUserIdOrderByCreatedAtDesc(userId)) {
            if (seen.add(r.getPurpose()) && r.isGranted()) {
                granted.add(r.getPurpose());
            }
        }
        return granted;
    }

    private Instant lastGrantBefore(UUID userId, ConsentPurpose purpose) {
        return records.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .filter(r -> r.getPurpose() == purpose && r.isGranted())
                .map(ConsentRecord::getCreatedAt)
                .findFirst()
                .orElse(null);
    }

    private static ConsentDto toDto(ConsentPurpose purpose, ConsentRecord latest, Instant lastGrant) {
        if (latest == null) {
            return new ConsentDto(purpose, false, NOTICE_VERSION, null, null, purpose.description());
        }
        if (latest.isGranted()) {
            return new ConsentDto(purpose, true, latest.getNoticeVersion(), latest.getCreatedAt(), null, purpose.description());
        }
        return new ConsentDto(purpose, false, latest.getNoticeVersion(), lastGrant, latest.getCreatedAt(), purpose.description());
    }
}
