package com.civix.services.service;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.civix.eligibility.domain.EligibilityEvaluation;
import com.civix.eligibility.repository.EligibilityEvaluationRepository;
import com.civix.services.domain.ServiceDefinition;
import com.civix.services.dto.DeadlineDto;
import com.civix.services.dto.EligibilitySummaryDto;
import com.civix.services.dto.ServiceJurisdictionDto;
import com.civix.services.dto.ServiceSummaryDto;

/** Maps service entities to API views, attaching the current user's latest eligibility outcome. */
@Component
public class ServiceViewMapper {

    private final EligibilityEvaluationRepository evaluations;

    public ServiceViewMapper(EligibilityEvaluationRepository evaluations) {
        this.evaluations = evaluations;
    }

    @Transactional(readOnly = true)
    public ServiceSummaryDto summary(ServiceDefinition s, UUID userId) {
        EligibilityEvaluation latest = userId == null ? null
                : evaluations.findFirstByUserIdAndServiceIdOrderByEvaluatedAtDesc(userId, s.getId()).orElse(null);
        return summary(s, latest);
    }

    @Transactional(readOnly = true)
    public List<ServiceSummaryDto> summaries(Collection<ServiceDefinition> services, UUID userId) {
        Map<UUID, EligibilityEvaluation> latest = latestFor(services, userId);
        return services.stream().map(s -> summary(s, latest.get(s.getId()))).toList();
    }

    @Transactional(readOnly = true)
    public Map<UUID, EligibilityEvaluation> latestFor(Collection<ServiceDefinition> services, UUID userId) {
        Map<UUID, EligibilityEvaluation> latest = new HashMap<>();
        if (userId == null || services.isEmpty()) {
            return latest;
        }
        List<UUID> ids = services.stream().map(ServiceDefinition::getId).toList();
        for (EligibilityEvaluation e : evaluations.findLatestForServices(userId, ids)) {
            latest.merge(e.getServiceId(), e, (a, b) -> a.getEvaluatedAt().isAfter(b.getEvaluatedAt()) ? a : b);
        }
        return latest;
    }

    public ServiceSummaryDto summary(ServiceDefinition s, EligibilityEvaluation latest) {
        return new ServiceSummaryDto(s.getId(), s.getCode(), s.getDefinitionVersion(), s.getName(), s.getSummary(),
                s.getDepartment(), s.getMinistry(), s.getCategory(), jurisdiction(s), s.getVerificationStatus(),
                s.getAutomationStatus(), s.getOfficialUrl(), s.isTestService(), deadline(s), s.getLastVerifiedAt(),
                latest == null ? null : new EligibilitySummaryDto(latest.getOutcome(), latest.getEvaluatedAt()));
    }

    public static ServiceJurisdictionDto jurisdiction(ServiceDefinition s) {
        return new ServiceJurisdictionDto(s.getJurisdictionLevel(), Arrays.asList(s.getStateCodes()));
    }

    public static DeadlineDto deadline(ServiceDefinition s) {
        Map<String, Object> d = s.getDeadline();
        if (d == null || d.isEmpty()) {
            return null;
        }
        return new DeadlineDto(str(d.get("date")), str(d.get("note")), str(d.get("sourceUrl")), str(d.get("verifiedAt")));
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }
}
