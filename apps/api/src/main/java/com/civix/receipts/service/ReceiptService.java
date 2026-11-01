package com.civix.receipts.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.civix.agent.domain.AgentTask;
import com.civix.agent.repository.AgentTaskRepository;
import com.civix.applications.domain.Application;
import com.civix.applications.domain.ApplicationStatus;
import com.civix.applications.repository.ApplicationRepository;
import com.civix.applications.service.ApplicationStateService;
import com.civix.audit.domain.ActorType;
import com.civix.audit.domain.AuditCategory;
import com.civix.audit.service.AuditEntry;
import com.civix.audit.service.AuditService;
import com.civix.common.api.ApiException;
import com.civix.common.storage.ObjectStorageService;
import com.civix.common.storage.ObjectStorageService.StoredObject;
import com.civix.receipts.domain.ApplicationReceipt;
import com.civix.receipts.dto.InternalReceiptRequest;
import com.civix.receipts.dto.ReceiptDto;
import com.civix.receipts.repository.ApplicationReceiptRepository;
import com.civix.services.repository.ServiceDefinitionRepository;

/** Receipts are stored only when the Verification Agent observed a reference on the portal. */
@Service
public class ReceiptService {

    private final ApplicationReceiptRepository receipts;
    private final ApplicationRepository applications;
    private final AgentTaskRepository tasks;
    private final ServiceDefinitionRepository services;
    private final ApplicationStateService state;
    private final ObjectStorageService storage;
    private final AuditService audit;
    private final Clock clock;

    public ReceiptService(ApplicationReceiptRepository receipts, ApplicationRepository applications, AgentTaskRepository tasks,
                          ServiceDefinitionRepository services, ApplicationStateService state, ObjectStorageService storage,
                          AuditService audit, Clock clock) {
        this.receipts = receipts;
        this.applications = applications;
        this.tasks = tasks;
        this.services = services;
        this.state = state;
        this.storage = storage;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public UUID createFromAgent(InternalReceiptRequest req) {
        AgentTask task = tasks.findById(req.taskId()).orElseThrow(() -> ApiException.notFound("task"));
        if (!task.getApplicationId().equals(req.applicationId())) {
            throw new ApiException("TASK_APPLICATION_MISMATCH", HttpStatus.UNPROCESSABLE_ENTITY, "The task does not belong to this application.");
        }
        Application app = applications.findByIdForUpdate(req.applicationId()).orElseThrow(() -> ApiException.notFound("application"));
        Optional<ApplicationReceipt> existing = receipts.findByApplicationIdAndReferenceNumber(app.getId(), req.referenceNumber());
        if (existing.isPresent()) {
            return existing.get().getId();
        }
        // A test service always yields a test receipt, whatever the caller claims.
        boolean test = req.isTestEnvironment() || app.isTestEnvironment()
                || services.findById(app.getServiceId()).map(s -> s.isTestService()).orElse(false);
        Instant now = clock.instant();
        ApplicationReceipt r = new ApplicationReceipt(app.getId(), task.getId(), req.referenceNumber(), req.acknowledgementText(),
                req.portalUrl() == null ? "" : req.portalUrl(), req.verificationMethod(),
                req.evidence() == null ? Map.of() : req.evidence(), test, now);
        if (req.screenshotId() != null) {
            String target = "receipts/" + app.getId() + "/" + r.getId() + ".png";
            if (storage.copy("tasks/" + task.getId() + "/screenshots/" + req.screenshotId() + ".png", target)) {
                r.attachArtifact(target);
            }
        }
        receipts.save(r);
        state.transition(app, ApplicationStatus.SUBMITTED, "Receipt verified on the portal");
        app.markSubmitted(req.referenceNumber(), test, now);
        audit.record(AuditEntry.of(AuditCategory.APPLICATION, "RECEIPT_STORED").user(app.getUserId())
                .actor(ActorType.AGENT, task.getId().toString()).resource("APPLICATION", app.getId())
                .summary("Acknowledgement verified" + (test ? " (test environment)" : "")).meta("verificationMethod", req.verificationMethod()));
        return r.getId();
    }

    @Transactional(readOnly = true)
    public List<ReceiptDto> list(UUID userId, UUID applicationId) {
        applications.findByIdAndUserId(applicationId, userId).orElseThrow(() -> ApiException.notFound("application"));
        return receipts.findByApplicationIdOrderByVerifiedAtDesc(applicationId).stream().map(ReceiptDto::from).toList();
    }

    @Transactional(readOnly = true)
    public StoredObject artifact(UUID userId, UUID applicationId, UUID receiptId) {
        applications.findByIdAndUserId(applicationId, userId).orElseThrow(() -> ApiException.notFound("application"));
        ApplicationReceipt r = receipts.findByIdAndApplicationId(receiptId, applicationId).orElseThrow(() -> ApiException.notFound("receipt"));
        if (r.getArtifactStorageKey() == null) {
            throw ApiException.notFound("receipt artifact");
        }
        return storage.get(r.getArtifactStorageKey()).orElseThrow(() -> ApiException.notFound("receipt artifact"));
    }
}
