package com.civix.services.service;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.civix.agent.client.AgentClient;
import com.civix.agent.client.AgentWire.DiscoveryFilters;
import com.civix.agent.client.AgentWire.DiscoveryRequest;
import com.civix.agent.client.AgentWire.DiscoveryResponse;
import com.civix.agent.client.AgentWire.DiscoveryResult;
import com.civix.citizen.dto.CitizenContext;
import com.civix.citizen.service.CitizenContextService;
import com.civix.consent.domain.ConsentPurpose;
import com.civix.consent.service.ConsentService;
import com.civix.eligibility.domain.EligibilityEvaluation;
import com.civix.eligibility.service.EligibilityService;
import com.civix.services.domain.ServiceDefinition;
import com.civix.services.dto.DiscoverRequest;
import com.civix.services.dto.DiscoveryResponseDto;
import com.civix.services.dto.ServiceSummaryDto;
import com.civix.services.repository.ServiceDefinitionRepository;

/**
 * Natural-language discovery, delegated to the agent. The citizen's profile is sent only with
 * {@code ELIGIBILITY_EVALUATION} (and {@code PROFILE_PROCESSING}) consent. Results are mapped to the services Spring
 * knows; unknown codes (registry newer than the last sync) are dropped.
 */
@Service
public class DiscoveryService {

    static final String NOTICE = "These suggestions are based on official sources and the information you provided. "
            + "They are guidance, not a decision: the responsible department decides eligibility.";
    private static final int LIMIT = 10;
    private static final Logger log = LoggerFactory.getLogger(DiscoveryService.class);

    private final AgentClient agent;
    private final ConsentService consents;
    private final CitizenContextService contexts;
    private final ServiceDefinitionRepository definitions;
    private final ServiceViewMapper mapper;
    private final EligibilityService eligibility;
    private final ServiceCatalogService catalog;
    private final Clock clock;

    public DiscoveryService(AgentClient agent, ConsentService consents, CitizenContextService contexts,
                            ServiceDefinitionRepository definitions, ServiceViewMapper mapper,
                            EligibilityService eligibility, ServiceCatalogService catalog, Clock clock) {
        this.agent = agent;
        this.consents = consents;
        this.contexts = contexts;
        this.definitions = definitions;
        this.mapper = mapper;
        this.eligibility = eligibility;
        this.catalog = catalog;
        this.clock = clock;
    }

    /** Not transactional on purpose: no database connection is held while the agent searches. */
    public DiscoveryResponseDto discover(UUID userId, DiscoverRequest request) {
        boolean withProfile = consents.has(userId, ConsentPurpose.ELIGIBILITY_EVALUATION);
        CitizenContext context = withProfile ? contexts.build(userId, ConsentPurpose.ELIGIBILITY_EVALUATION) : null;
        boolean includeTest = catalog.testServicesEnabled();
        DiscoveryResponse response = agent.discover(new DiscoveryRequest(request.query().strip(),
                new DiscoveryFilters(request.state(), request.category() == null ? null : request.category().name(), null),
                context, includeTest, LIMIT));

        List<DiscoveryResult> results = response == null || response.results() == null ? List.of() : response.results();
        Set<String> codes = new LinkedHashSet<>();
        results.forEach(r -> {
            if (r.serviceCode() != null) {
                codes.add(r.serviceCode());
            }
        });
        Map<String, ServiceDefinition> byCode = new HashMap<>();
        if (!codes.isEmpty()) {
            definitions.findVisibleByCodes(codes, includeTest).forEach(s -> byCode.put(s.getCode(), s));
        }

        List<DiscoveryResponseDto.Result> out = new ArrayList<>();
        for (DiscoveryResult r : results) {
            ServiceDefinition service = byCode.get(r.serviceCode());
            if (service == null) {
                log.info("Discovery returned unknown or hidden service code {}; skipped", r.serviceCode());
                continue;
            }
            EligibilityEvaluation saved = null;
            if (withProfile && r.eligibility() != null && service.getCode().equals(r.eligibility().serviceCode())) {
                saved = eligibility.persist(userId, service, r.eligibility());
            }
            ServiceSummaryDto summary = saved != null ? mapper.summary(service, saved) : mapper.summary(service, userId);
            out.add(new DiscoveryResponseDto.Result(summary, r.score() == null ? 0.0 : r.score(),
                    r.matchReasons() == null ? List.of() : r.matchReasons(), withProfile ? r.eligibility() : null));
        }

        DiscoveryResponseDto.Interpretation interpretation = response == null || response.interpretation() == null
                ? new DiscoveryResponseDto.Interpretation(null, List.of(), List.of(), request.state(), false)
                : new DiscoveryResponseDto.Interpretation(response.interpretation().summary(),
                        nullToEmpty(response.interpretation().categories()), nullToEmpty(response.interpretation().keywords()),
                        response.interpretation().state(), Boolean.TRUE.equals(response.interpretation().usedLlm()));
        String generatedAt = response != null && response.generatedAt() != null ? response.generatedAt()
                : clock.instant().toString();
        return new DiscoveryResponseDto(interpretation, out, generatedAt, NOTICE);
    }

    private static List<String> nullToEmpty(List<String> l) {
        return l == null ? List.of() : l;
    }
}
