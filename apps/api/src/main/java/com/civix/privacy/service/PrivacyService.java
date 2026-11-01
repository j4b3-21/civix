package com.civix.privacy.service;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.civix.agent.client.AgentClient;
import com.civix.agent.domain.AgentTask;
import com.civix.agent.domain.TaskStatus;
import com.civix.agent.repository.AgentTaskRepository;
import com.civix.applications.repository.ApplicationRepository;
import com.civix.audit.domain.ActorType;
import com.civix.audit.domain.AuditCategory;
import com.civix.audit.service.AuditEntry;
import com.civix.audit.service.AuditService;
import com.civix.auth.repository.UserRepository;
import com.civix.auth.service.RefreshTokenService;
import com.civix.citizen.service.ProfileService;
import com.civix.common.api.ApiException;
import com.civix.common.crypto.Hashing;
import com.civix.common.storage.ObjectStorageService;
import com.civix.consent.repository.ConsentRecordRepository;
import com.civix.documents.domain.Document;
import com.civix.documents.repository.DocumentRepository;
import com.civix.documents.service.DocumentService;
import com.civix.eligibility.repository.EligibilityEvaluationRepository;
import com.civix.notifications.repository.NotificationRepository;
import com.civix.privacy.dto.DeletionRequest.DeletionScope;
import com.civix.privacy.dto.DeletionResultDto;

/**
 * Citizen-initiated deletion. DOCUMENTS removes files and their extractions; PROFILE also removes every profile
 * value; ACCOUNT also removes applications, agent runs, screenshots, receipts, evaluations, notifications and
 * consents, revokes sessions and anonymises the user row. Audit events are kept (pseudonymous) for accountability.
 * Whether these procedures satisfy a specific legal obligation (e.g. DPDP Act rules) still needs legal review.
 */
@Service
public class PrivacyService {

    private static final Logger log = LoggerFactory.getLogger(PrivacyService.class);

    private final DocumentRepository documents;
    private final DocumentService documentService;
    private final ProfileService profiles;
    private final ApplicationRepository applications;
    private final AgentTaskRepository tasks;
    private final EligibilityEvaluationRepository evaluations;
    private final NotificationRepository notifications;
    private final ConsentRecordRepository consents;
    private final UserRepository users;
    private final RefreshTokenService refreshTokens;
    private final ObjectStorageService storage;
    private final AgentClient agent;
    private final AuditService audit;

    public PrivacyService(DocumentRepository documents, DocumentService documentService, ProfileService profiles,
                          ApplicationRepository applications, AgentTaskRepository tasks, EligibilityEvaluationRepository evaluations,
                          NotificationRepository notifications, ConsentRecordRepository consents, UserRepository users,
                          RefreshTokenService refreshTokens, ObjectStorageService storage, AgentClient agent, AuditService audit) {
        this.documents = documents;
        this.documentService = documentService;
        this.profiles = profiles;
        this.applications = applications;
        this.tasks = tasks;
        this.evaluations = evaluations;
        this.notifications = notifications;
        this.consents = consents;
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.storage = storage;
        this.agent = agent;
        this.audit = audit;
    }

    @Transactional
    public DeletionResultDto delete(UUID userId, DeletionScope scope) {
        int docs = 0;
        int fields = 0;
        boolean account = false;
        List<Document> active = documents.findByUserIdAndDeletedAtIsNullOrderByUploadedAtDesc(userId);
        for (Document d : active) {
            documentService.delete(userId, d.getId());
            docs++;
        }
        if (scope == DeletionScope.PROFILE || scope == DeletionScope.ACCOUNT) {
            fields = profiles.deleteAll(userId);
        }
        if (scope == DeletionScope.ACCOUNT) {
            for (AgentTask t : tasks.findByUserIdAndStatusIn(userId, TaskStatus.ACTIVE)) {
                try {
                    agent.cancel(t.getId(), "ACCOUNT_DELETED");
                } catch (RuntimeException e) {
                    log.warn("Could not cancel task {} during account deletion: {}", t.getId(), e.getClass().getSimpleName());
                }
            }
            for (UUID taskId : tasks.findIdsByUserId(userId)) {
                storage.deletePrefix("tasks/" + taskId + "/");
            }
            for (UUID appId : applications.findIdsByUserAndStatus(userId, java.util.EnumSet.allOf(com.civix.applications.domain.ApplicationStatus.class))) {
                storage.deletePrefix("receipts/" + appId + "/");
            }
            storage.deletePrefix("users/" + userId + "/");
            applications.deleteAllForUser(userId);
            documents.deleteAllForUser(userId);
            evaluations.deleteAllForUser(userId);
            notifications.deleteAllForUser(userId);
            consents.deleteAllForUser(userId);
            refreshTokens.revokeAll(userId);
            users.findById(userId).orElseThrow(() -> ApiException.notFound("user"))
                    .markDeleted("{noop}!" + Hashing.randomToken(32));
            account = true;
        }
        audit.record(AuditEntry.of(AuditCategory.PRIVACY, "DATA_DELETED").user(account ? null : userId)
                .actor(ActorType.USER, userId.toString()).summary("Deleted " + scope.name().toLowerCase() + " data")
                .meta("documents", docs).meta("profileFields", fields).meta("scope", scope.name()));
        return new DeletionResultDto(scope.name(), docs, fields, account);
    }
}
