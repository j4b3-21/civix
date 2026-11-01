package com.civix.services.service;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.civix.common.api.ApiException;
import com.civix.common.api.PageResponse;
import com.civix.common.api.Pagination;
import com.civix.common.json.JsonCodec;
import com.civix.config.CivixProperties;
import com.civix.services.domain.EligibilityRule;
import com.civix.services.domain.JurisdictionLevel;
import com.civix.services.domain.ServiceCategory;
import com.civix.services.domain.ServiceDefinition;
import com.civix.services.domain.ServiceVerificationStatus;
import com.civix.services.dto.CategoryCountDto;
import com.civix.services.dto.JurisdictionDto;
import com.civix.services.dto.ServiceDetailDto;
import com.civix.services.dto.ServiceSummaryDto;
import com.civix.services.repository.EligibilityRuleRepository;
import com.civix.services.repository.ServiceDefinitionRepository;
import com.civix.services.repository.ServiceSearchRepository;
import com.civix.services.repository.ServiceSourceRepository;

/** Read side of the service registry. Test services are visible only when {@code CIVIX_ENABLE_TEST_SERVICES=true}. */
@Service
public class ServiceCatalogService {

    static final String DISCLAIMER = "CIVIX is not a government website. This information is compiled from the official "
            + "sources listed below; eligibility results are guidance only and the responsible department decides. "
            + "Always confirm details on the official portal.";
    static final String DRAFT_NOTE = " The sources for this service have not been fully verified yet.";
    static final String TEST_DISCLAIMER = "This is the CIVIX Test Portal, an authorized local test environment. It is not a "
            + "government service and nothing submitted here reaches any government system.";

    private final ServiceDefinitionRepository definitions;
    private final ServiceSourceRepository sources;
    private final EligibilityRuleRepository rules;
    private final ServiceSearchRepository search;
    private final ServiceViewMapper mapper;
    private final JsonCodec json;
    private final boolean includeTest;

    public ServiceCatalogService(ServiceDefinitionRepository definitions, ServiceSourceRepository sources,
                                 EligibilityRuleRepository rules, ServiceSearchRepository search,
                                 ServiceViewMapper mapper, JsonCodec json, CivixProperties props) {
        this.definitions = definitions;
        this.sources = sources;
        this.rules = rules;
        this.search = search;
        this.mapper = mapper;
        this.json = json;
        this.includeTest = props.enableTestServices();
    }

    public boolean testServicesEnabled() {
        return includeTest;
    }

    @Transactional(readOnly = true)
    public PageResponse<ServiceSummaryDto> list(String q, ServiceCategory category, JurisdictionLevel level,
                                                String state, Integer page, Integer size, UUID userId) {
        String query = q == null || q.isBlank() ? null : q.strip();
        if (query != null && query.length() > 200) {
            throw ApiException.validation("q", "must be at most 200 characters");
        }
        if (state != null && !state.isBlank() && !JurisdictionCatalog.isValid(state.strip())) {
            throw ApiException.validation("state", "must be an ISO 3166-2:IN code such as IN-MH");
        }
        String stateCode = state == null || state.isBlank() ? null : state.strip();
        int p = Pagination.page(page);
        int s = Pagination.size(size);
        ServiceSearchRepository.Result result = search.search(
                new ServiceSearchRepository.Criteria(query, category, level, stateCode, includeTest), p, s);
        return PageResponse.of(mapper.summaries(result.items(), userId), p, s, result.total());
    }

    /** A service that is active and visible to citizens, or 404. */
    @Transactional(readOnly = true)
    public ServiceDefinition visible(UUID serviceId) {
        return definitions.findVisibleById(serviceId, includeTest)
                .orElseThrow(() -> ApiException.notFound("Service"));
    }

    @Transactional(readOnly = true)
    public ServiceDetailDto detail(UUID serviceId, UUID userId) {
        ServiceDefinition s = visible(serviceId);
        ServiceSummaryDto sum = mapper.summary(s, userId);
        Map<String, Object> def = s.getDefinition() == null ? Map.of() : s.getDefinition();

        List<ServiceDetailDto.EligibilityRuleDto> ruleDtos = rules.findByServiceIdOrderByRuleIdAsc(s.getId()).stream()
                .map((EligibilityRule r) -> new ServiceDetailDto.EligibilityRuleDto(r.getRuleId(), r.getRuleVersion(),
                        r.getDescription(), r.getFieldKey(), r.getOperator().name(), json.readValue(r.getValueJson()),
                        r.getSourceUrl()))
                .toList();
        List<ServiceDetailDto.RequiredDocumentDto> documents = new ArrayList<>();
        for (Map<String, Object> d : listOfMaps(def.get("requiredDocuments"))) {
            documents.add(new ServiceDetailDto.RequiredDocumentDto(str(d.get("category")), str(d.get("description")),
                    Boolean.TRUE.equals(d.get("mandatory"))));
        }
        List<ServiceDetailDto.ProcedureStepDto> procedure = new ArrayList<>();
        for (Map<String, Object> st : listOfMaps(def.get("procedure"))) {
            procedure.add(new ServiceDetailDto.ProcedureStepDto(intValue(st.get("order")), str(st.get("title")),
                    str(st.get("description"))));
        }
        List<ServiceDetailDto.SourceDto> sourceDtos = sources.findByServiceIdOrderByTitleAsc(s.getId()).stream()
                .map(src -> new ServiceDetailDto.SourceDto(src.getUrl(), src.getTitle(), src.getKind().name(),
                        src.getRetrievedAt()))
                .toList();
        List<String> benefits = def.get("benefits") instanceof List<?> l ? l.stream().map(String::valueOf).toList() : List.of();

        String disclaimer = s.isTestService() ? TEST_DISCLAIMER
                : DISCLAIMER + (s.getVerificationStatus() == ServiceVerificationStatus.DRAFT ? DRAFT_NOTE : "");

        return new ServiceDetailDto(sum.id(), sum.code(), sum.version(), sum.name(), sum.summary(), sum.department(),
                sum.ministry(), sum.category(), sum.jurisdiction(), sum.verificationStatus(), sum.automationStatus(),
                sum.officialUrl(), sum.isTestService(), sum.deadline(), sum.lastVerifiedAt(), sum.eligibility(),
                s.getDescription(), benefits, ruleDtos, documents, procedure, sourceDtos,
                Arrays.asList(s.getPortalDomains()), disclaimer);
    }

    @Transactional(readOnly = true)
    public List<CategoryCountDto> categories() {
        Map<ServiceCategory, Long> counts = new EnumMap<>(ServiceCategory.class);
        for (Object[] row : definitions.countByCategory(includeTest)) {
            counts.put((ServiceCategory) row[0], ((Number) row[1]).longValue());
        }
        List<CategoryCountDto> result = new ArrayList<>();
        for (ServiceCategory c : ServiceCategory.values()) {
            result.add(new CategoryCountDto(c, counts.getOrDefault(c, 0L)));
        }
        return result;
    }

    public List<JurisdictionDto> jurisdictions() {
        return JurisdictionCatalog.ALL.stream()
                .map(j -> new JurisdictionDto(j.code(), j.name(), j.type().name()))
                .toList();
    }

    /** Visible services with a verified upcoming deadline date, soonest first. */
    @Transactional(readOnly = true)
    public List<ServiceDefinition> withUpcomingVerifiedDeadline(LocalDate today, int limit) {
        record Due(ServiceDefinition service, LocalDate date) {
        }
        List<Due> due = new ArrayList<>();
        for (ServiceDefinition s : definitions.findVisible(includeTest)) {
            Map<String, Object> d = s.getDeadline();
            if (d == null || d.get("date") == null || d.get("verifiedAt") == null) {
                continue;
            }
            try {
                LocalDate date = LocalDate.parse(d.get("date").toString());
                if (!date.isBefore(today)) {
                    due.add(new Due(s, date));
                }
            } catch (DateTimeParseException ignored) {
                // malformed date in a definition: never shown as a deadline
            }
        }
        return due.stream()
                .sorted((a, b) -> a.date().compareTo(b.date()))
                .limit(limit)
                .map(Due::service)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ServiceDefinition> visibleByIds(List<UUID> ids) {
        return definitions.findAllById(ids).stream()
                .filter(ServiceDefinition::isActive)
                .filter(s -> includeTest || !s.isTestService())
                .toList();
    }

    private static List<Map<String, Object>> listOfMaps(Object o) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (o instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> m) {
                    Map<String, Object> copy = new java.util.LinkedHashMap<>();
                    m.forEach((k, v) -> copy.put(String.valueOf(k), v));
                    out.add(copy);
                }
            }
        }
        return out;
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }

    private static int intValue(Object o) {
        return o instanceof Number n ? n.intValue() : 0;
    }
}
