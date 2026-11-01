package com.civix.eligibility.service;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.civix.agent.client.AgentClient;
import com.civix.agent.client.AgentWire.EligibilityRequest;
import com.civix.agent.client.AgentUnavailableException;
import com.civix.citizen.dto.CitizenContext;
import com.civix.citizen.service.CitizenContextService;
import com.civix.common.json.JsonCodec;
import com.civix.consent.domain.ConsentPurpose;
import com.civix.consent.service.ConsentService;
import com.civix.eligibility.domain.EligibilityEvaluation;
import com.civix.eligibility.domain.EligibilityOutcome;
import com.civix.eligibility.dto.EligibilityResultDto;
import com.civix.eligibility.repository.EligibilityEvaluationRepository;
import com.civix.services.domain.ServiceDefinition;
import com.civix.services.service.ServiceCatalogService;

/**
 * Eligibility is evaluated deterministically by the agent ({@code app/policies/eligibility.py}); Spring persists
 * and serves the results. Requires {@code ELIGIBILITY_EVALUATION} consent.
 */
@Service
public class EligibilityService {

    private static final Logger log = LoggerFactory.getLogger(EligibilityService.class);

    private final AgentClient agent;
    private final ServiceCatalogService catalog;
    private final CitizenContextService contexts;
    private final ConsentService consents;
    private final EligibilityEvaluationRepository evaluations;
    private final JsonCodec json;
    private final Clock clock;

    public EligibilityService(AgentClient agent, ServiceCatalogService catalog, CitizenContextService contexts,
                              ConsentService consents, EligibilityEvaluationRepository evaluations, JsonCodec json,
                              Clock clock) {
        this.agent = agent;
        this.catalog = catalog;
        this.contexts = contexts;
        this.consents = consents;
        this.evaluations = evaluations;
        this.json = json;
        this.clock = clock;
    }

    /** Not transactional on purpose: no database connection is held while the agent evaluates. */
    public EligibilityResultDto evaluate(UUID userId, UUID serviceId) {
        consents.require(userId, ConsentPurpose.ELIGIBILITY_EVALUATION);
        ServiceDefinition service = catalog.visible(serviceId);
        CitizenContext context = contexts.build(userId, ConsentPurpose.ELIGIBILITY_EVALUATION);
        EligibilityResultDto result = agent.evaluateEligibility(new EligibilityRequest(service.getCode(), context));
        if (result == null || !service.getCode().equals(result.serviceCode())) {
            throw new AgentUnavailableException("The eligibility engine returned a result for a different service.", null);
        }
        persist(userId, service, result);
        return result;
    }

    /** Persists an agent-produced result (also used by discovery). Returns {@code null} if the result is unusable. */
    @Transactional
    public EligibilityEvaluation persist(UUID userId, ServiceDefinition service, EligibilityResultDto result) {
        EligibilityOutcome outcome;
        try {
            outcome = EligibilityOutcome.valueOf(result.outcome());
        } catch (IllegalArgumentException | NullPointerException e) {
            log.warn("Ignoring eligibility result with unknown outcome for {}", service.getCode());
            return null;
        }
        Map<String, Object> asMap = json.toMap(result);
        int version = result.serviceVersion() != null ? result.serviceVersion() : service.getDefinitionVersion();
        String engine = result.engineVersion() == null ? "unknown" : result.engineVersion();
        if (engine.length() > 16) {
            engine = engine.substring(0, 16);
        }
        return evaluations.save(new EligibilityEvaluation(userId, service.getId(), version, outcome, asMap, engine,
                parseInstant(result.evaluatedAt())));
    }

    private Instant parseInstant(String value) {
        if (value == null) {
            return clock.instant();
        }
        try {
            return OffsetDateTime.parse(value).toInstant();
        } catch (DateTimeParseException e) {
            try {
                return Instant.parse(value);
            } catch (DateTimeParseException e2) {
                return clock.instant();
            }
        }
    }
}
