package com.civix.services.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.civix.agent.client.AgentClient;
import com.civix.agent.client.AgentWire.RegistryServicesResponse;
import com.civix.citizen.service.ProfileFieldCatalogService;
import com.civix.common.api.ApiException;
import com.civix.common.crypto.Hashing;
import com.civix.common.json.JsonCodec;
import com.civix.config.CivixProperties;
import com.civix.services.domain.AutomationStatus;
import com.civix.services.domain.EligibilityRule;
import com.civix.services.domain.JurisdictionLevel;
import com.civix.services.domain.RuleOperator;
import com.civix.services.domain.ServiceCategory;
import com.civix.services.domain.ServiceDefinition;
import com.civix.services.domain.ServiceSource;
import com.civix.services.domain.ServiceVerificationStatus;
import com.civix.services.domain.SourceKind;
import com.civix.services.dto.RegistrySyncResultDto;
import com.civix.services.repository.EligibilityRuleRepository;
import com.civix.services.repository.ServiceDefinitionRepository;
import com.civix.services.repository.ServiceSourceRepository;

/**
 * Syncs the validated service registry from the agent ({@code GET /internal/v1/registry/services}) into
 * {@code service_definitions}, {@code service_sources} and {@code eligibility_rules}. Upserts by (code, version)
 * using a content hash; deactivates codes that are no longer published. Each definition is applied in its own
 * transaction so one bad definition never blocks the others.
 */
@Service
public class RegistrySyncService {

    private static final Logger log = LoggerFactory.getLogger(RegistrySyncService.class);
    private static final Pattern CODE = Pattern.compile("^IN_[A-Z0-9]+(_[A-Z0-9]+)+$");
    private static final Pattern URL = Pattern.compile("^https?://\\S+$");

    private final AgentClient agent;
    private final ServiceDefinitionRepository definitions;
    private final ServiceSourceRepository sources;
    private final EligibilityRuleRepository rules;
    private final ProfileFieldCatalogService profileCatalog;
    private final TransactionTemplate tx;
    private final JsonCodec json;
    private final Clock clock;
    private final boolean includeTest;
    private final boolean syncOnStartup;
    private final Duration retryInterval;
    private final AtomicBoolean running = new AtomicBoolean();
    private volatile Instant lastSuccessfulSync;

    public RegistrySyncService(AgentClient agent, ServiceDefinitionRepository definitions, ServiceSourceRepository sources,
                               EligibilityRuleRepository rules, ProfileFieldCatalogService profileCatalog,
                               PlatformTransactionManager txManager, JsonCodec json, Clock clock, CivixProperties props) {
        this.agent = agent;
        this.definitions = definitions;
        this.sources = sources;
        this.rules = rules;
        this.profileCatalog = profileCatalog;
        this.tx = new TransactionTemplate(txManager);
        this.json = json;
        this.clock = clock;
        this.includeTest = props.enableTestServices();
        this.syncOnStartup = props.registry().syncOnStartup();
        this.retryInterval = Duration.ofSeconds(Math.max(5, props.registry().retryIntervalSeconds()));
    }

    /** Non-blocking initial sync: retries every {@code retry-interval-seconds} until the agent answers. */
    @EventListener(ApplicationReadyEvent.class)
    public void syncOnStartup() {
        if (!syncOnStartup) {
            return;
        }
        Thread.ofVirtual().name("registry-initial-sync").start(() -> {
            while (lastSuccessfulSync == null) {
                try {
                    RegistrySyncResultDto result = syncAll();
                    log.info("Initial registry sync: {} synced, {} deactivated, {} error(s)",
                            result.synced(), result.deactivated(), result.errors().size());
                } catch (RuntimeException e) {
                    log.warn("Registry sync failed ({}); retrying in {}s", e.getClass().getSimpleName(),
                            retryInterval.toSeconds());
                }
                if (lastSuccessfulSync == null) {
                    try {
                        Thread.sleep(retryInterval);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        });
    }

    public Optional<Instant> lastSuccessfulSync() {
        return Optional.ofNullable(lastSuccessfulSync);
    }

    public RegistrySyncResultDto syncAll() {
        if (!running.compareAndSet(false, true)) {
            throw new ApiException("SYNC_IN_PROGRESS", HttpStatus.CONFLICT, "A registry sync is already running");
        }
        try {
            List<String> errors = new ArrayList<>(profileCatalog.sync());
            RegistryServicesResponse response = agent.fetchServices(includeTest);
            if (response == null || response.services() == null) {
                throw new ApiException("REGISTRY_EMPTY", HttpStatus.BAD_GATEWAY, "The registry response was empty");
            }
            Set<String> publishedCodes = new HashSet<>();
            int synced = 0;
            for (Map<String, Object> raw : response.services()) {
                String code = raw.get("code") == null ? null : raw.get("code").toString();
                try {
                    ParsedDefinition parsed = parse(raw);
                    if (parsed.content().testService() && !includeTest) {
                        continue; // never materialise test services unless explicitly enabled
                    }
                    publishedCodes.add(parsed.code());
                    tx.executeWithoutResult(status -> upsert(parsed, raw));
                    synced++;
                } catch (InvalidDefinitionException e) {
                    errors.add(label(raw) + ": " + e.getMessage());
                    if (code != null) {
                        publishedCodes.add(code); // keep the previously synced version visible
                    }
                } catch (RuntimeException e) {
                    log.warn("Could not apply service definition {}: {}", label(raw), e.toString());
                    errors.add(label(raw) + ": could not be stored (" + e.getClass().getSimpleName() + ")");
                    if (code != null) {
                        publishedCodes.add(code);
                    }
                }
            }
            Integer deactivated = tx.execute(status -> deactivateMissing(publishedCodes));
            lastSuccessfulSync = clock.instant();
            return new RegistrySyncResultDto(synced, deactivated == null ? 0 : deactivated, errors);
        } finally {
            running.set(false);
        }
    }

    // ---- upsert ------------------------------------------------------------------------------------

    private void upsert(ParsedDefinition p, Map<String, Object> raw) {
        Instant now = clock.instant();
        String hash = Hashing.sha256Hex(json.canonical(raw));
        // Only one active row per code (partial unique index): retire other versions first and flush, because
        // Hibernate would otherwise execute the insert of the new version before these updates.
        for (ServiceDefinition other : definitions.findByCode(p.code())) {
            if (other.getDefinitionVersion() != p.version() && other.isActive()) {
                other.setActive(false, now);
            }
        }
        definitions.flush();

        Optional<ServiceDefinition> existing = definitions.findByCodeAndDefinitionVersion(p.code(), p.version());
        ServiceDefinition service;
        boolean contentChanged;
        if (existing.isPresent()) {
            service = existing.get();
            contentChanged = !hash.equals(service.getContentHash());
            if (contentChanged) {
                service.apply(p.content(), new LinkedHashMap<>(raw), hash, now);
            }
            service.setActive(true, now);
            definitions.saveAndFlush(service);
        } else {
            service = new ServiceDefinition(p.code(), p.version(), now);
            service.apply(p.content(), new LinkedHashMap<>(raw), hash, now);
            service = definitions.saveAndFlush(service);
            contentChanged = true;
        }
        if (contentChanged) {
            sources.deleteAllForService(service.getId());
            for (ParsedSource s : p.sources()) {
                sources.save(new ServiceSource(service.getId(), s.url(), s.title(), s.kind(), s.retrievedAt()));
            }
            rules.deleteAllForService(service.getId());
            for (ParsedRule r : p.rules()) {
                rules.save(new EligibilityRule(service.getId(), r.id(), r.version(), r.field(), r.operator(),
                        json.writeNullable(r.value()), r.description(), r.sourceUrl()));
            }
        }
    }

    private int deactivateMissing(Set<String> publishedCodes) {
        Instant now = clock.instant();
        int count = 0;
        for (ServiceDefinition s : definitions.findByActiveTrue()) {
            if (!publishedCodes.contains(s.getCode())) {
                s.setActive(false, now);
                count++;
            }
        }
        return count;
    }

    // ---- parsing & validation ------------------------------------------------------------------------

    record ParsedDefinition(String code, int version, ServiceDefinition.ServiceContent content,
                            List<ParsedSource> sources, List<ParsedRule> rules) {
    }

    record ParsedSource(String id, String url, String title, SourceKind kind, LocalDate retrievedAt) {
    }

    record ParsedRule(String id, int version, String field, RuleOperator operator, Object value, String description,
                      String sourceUrl) {
    }

    static final class InvalidDefinitionException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        InvalidDefinitionException(String message) {
            super(message);
        }
    }

    static ParsedDefinition parse(Map<String, Object> raw) {
        String code = requiredString(raw, "code", 80);
        if (!CODE.matcher(code).matches()) {
            throw new InvalidDefinitionException("code does not match ^IN_[A-Z0-9]+(_[A-Z0-9]+)+$");
        }
        int version = requiredInt(raw, "version");
        if (version < 1) {
            throw new InvalidDefinitionException("version must be >= 1");
        }
        String name = requiredString(raw, "name", 255);
        String summary = requiredString(raw, "summary", 500);
        String description = requiredString(raw, "description", Integer.MAX_VALUE);
        String department = requiredString(raw, "department", 255);
        String ministry = optionalString(raw, "ministry", 255);
        ServiceCategory category = enumValue(ServiceCategory.class, raw.get("category"), "category");

        Map<String, Object> jurisdiction = map(raw.get("jurisdiction"), "jurisdiction");
        JurisdictionLevel level = enumValue(JurisdictionLevel.class, jurisdiction.get("level"), "jurisdiction.level");
        List<String> states = stringList(jurisdiction.get("states"));
        for (String st : states) {
            if (!JurisdictionCatalog.isValid(st)) {
                throw new InvalidDefinitionException("unknown state code " + st);
            }
        }
        if (level == JurisdictionLevel.STATE && states.isEmpty()) {
            throw new InvalidDefinitionException("STATE services must list at least one state");
        }

        String officialUrl = requiredString(raw, "officialUrl", 500);
        if (!URL.matcher(officialUrl).matches()) {
            throw new InvalidDefinitionException("officialUrl must be an http(s) URL");
        }
        List<String> portalDomains = stringList(raw.get("portalDomains"));
        ServiceVerificationStatus verification = enumValue(ServiceVerificationStatus.class,
                raw.get("verificationStatus"), "verificationStatus");
        AutomationStatus automation = enumValue(AutomationStatus.class, raw.get("automationStatus"), "automationStatus");
        if (!(raw.get("isTestService") instanceof Boolean testService)) {
            throw new InvalidDefinitionException("isTestService must be a boolean");
        }
        if (testService && automation == AutomationStatus.ACTIVE) {
            throw new InvalidDefinitionException("test services can never be ACTIVE");
        }
        if (automation == AutomationStatus.TEST_ONLY && !testService) {
            throw new InvalidDefinitionException("TEST_ONLY automation is reserved for test services");
        }
        LocalDate lastVerifiedAt = optionalDate(raw.get("lastVerifiedAt"), "lastVerifiedAt");

        Map<String, Object> deadline = null;
        if (raw.get("deadline") != null) {
            Map<String, Object> d = map(raw.get("deadline"), "deadline");
            optionalDate(d.get("date"), "deadline.date");
            optionalDate(d.get("verifiedAt"), "deadline.verifiedAt");
            deadline = new LinkedHashMap<>(d);
        }

        List<ParsedSource> parsedSources = new ArrayList<>();
        Map<String, ParsedSource> sourcesById = new HashMap<>();
        Set<String> urls = new LinkedHashSet<>();
        for (Map<String, Object> s : mapList(raw.get("sources"), "sources")) {
            String url = requiredString(s, "url", 1000);
            if (!URL.matcher(url).matches()) {
                throw new InvalidDefinitionException("source url must be an http(s) URL");
            }
            String title = truncate(requiredString(s, "title", Integer.MAX_VALUE), 500);
            SourceKind kind = enumValue(SourceKind.class, s.get("kind"), "sources.kind");
            LocalDate retrieved = optionalDate(s.get("retrievedAt"), "sources.retrievedAt");
            if (retrieved == null) {
                throw new InvalidDefinitionException("every source needs retrievedAt");
            }
            ParsedSource ps = new ParsedSource(s.get("id") == null ? null : s.get("id").toString(), url, title, kind, retrieved);
            if (ps.id() != null) {
                sourcesById.put(ps.id(), ps);
            }
            if (urls.add(url)) {
                parsedSources.add(ps);
            }
        }
        if (parsedSources.isEmpty()) {
            throw new InvalidDefinitionException("at least one source is required");
        }

        Map<String, Object> eligibility = map(raw.get("eligibility"), "eligibility");
        List<ParsedRule> parsedRules = new ArrayList<>();
        Set<String> ruleIds = new HashSet<>();
        for (Map<String, Object> r : mapList(eligibility.get("rules"), "eligibility.rules")) {
            String id = requiredString(r, "id", 64);
            if (!ruleIds.add(id)) {
                throw new InvalidDefinitionException("duplicate rule id " + id);
            }
            int ruleVersion = r.get("version") == null ? 1 : requiredInt(r, "version");
            String field = requiredString(r, "field", 64);
            RuleOperator operator = enumValue(RuleOperator.class, r.get("operator"), "rule.operator");
            String ruleDescription = truncate(requiredString(r, "description", Integer.MAX_VALUE), 1000);
            String sourceUrl = optionalString(r, "sourceUrl", 1000);
            if (sourceUrl == null && r.get("sourceId") != null) {
                ParsedSource src = sourcesById.get(r.get("sourceId").toString());
                sourceUrl = src == null ? null : src.url();
            }
            if (sourceUrl == null && verification != ServiceVerificationStatus.DRAFT
                    && verification != ServiceVerificationStatus.DEPRECATED) {
                throw new InvalidDefinitionException("verified services must cite an official source for rule " + id);
            }
            parsedRules.add(new ParsedRule(id, ruleVersion, field, operator, r.get("value"), ruleDescription, sourceUrl));
        }

        ServiceDefinition.ServiceContent content = new ServiceDefinition.ServiceContent(name, summary, description,
                department, ministry, category, level, states.toArray(String[]::new), officialUrl,
                portalDomains.toArray(String[]::new), verification, automation, testService, deadline, lastVerifiedAt);
        return new ParsedDefinition(code, version, content, parsedSources, parsedRules);
    }

    private static String label(Map<String, Object> raw) {
        Object code = raw.get("code");
        Object version = raw.get("version");
        return (code == null ? "<no code>" : code.toString()) + (version == null ? "" : " v" + version);
    }

    private static String requiredString(Map<String, Object> m, String key, int maxLength) {
        Object v = m.get(key);
        if (!(v instanceof String s) || s.isBlank()) {
            throw new InvalidDefinitionException(key + " is required");
        }
        if (s.length() > maxLength) {
            throw new InvalidDefinitionException(key + " exceeds " + maxLength + " characters");
        }
        return s;
    }

    private static String optionalString(Map<String, Object> m, String key, int maxLength) {
        Object v = m.get(key);
        if (v == null) {
            return null;
        }
        if (!(v instanceof String s)) {
            throw new InvalidDefinitionException(key + " must be a string");
        }
        if (s.length() > maxLength) {
            throw new InvalidDefinitionException(key + " exceeds " + maxLength + " characters");
        }
        return s.isBlank() ? null : s;
    }

    private static int requiredInt(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v instanceof Integer || v instanceof Long || v instanceof Short) {
            return ((Number) v).intValue();
        }
        throw new InvalidDefinitionException(key + " must be an integer");
    }

    private static LocalDate optionalDate(Object v, String key) {
        if (v == null) {
            return null;
        }
        String s = v.toString();
        try {
            return LocalDate.parse(s.length() > 10 ? s.substring(0, 10) : s);
        } catch (DateTimeParseException e) {
            throw new InvalidDefinitionException(key + " must be an ISO date");
        }
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, Object v, String key) {
        if (v == null) {
            throw new InvalidDefinitionException(key + " is required");
        }
        try {
            return Enum.valueOf(type, v.toString());
        } catch (IllegalArgumentException e) {
            throw new InvalidDefinitionException(key + " has unknown value " + v);
        }
    }

    private static Map<String, Object> map(Object v, String key) {
        if (!(v instanceof Map<?, ?> m)) {
            throw new InvalidDefinitionException(key + " must be an object");
        }
        Map<String, Object> out = new LinkedHashMap<>();
        m.forEach((k, val) -> out.put(String.valueOf(k), val));
        return out;
    }

    private static List<Map<String, Object>> mapList(Object v, String key) {
        if (v == null) {
            return List.of();
        }
        if (!(v instanceof List<?> list)) {
            throw new InvalidDefinitionException(key + " must be a list");
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object item : list) {
            out.add(map(item, key + "[]"));
        }
        return out;
    }

    private static List<String> stringList(Object v) {
        if (v == null) {
            return List.of();
        }
        if (!(v instanceof List<?> list)) {
            throw new InvalidDefinitionException("expected a list of strings");
        }
        return list.stream().map(String::valueOf).distinct().toList();
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
