package com.civix.citizen.service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.civix.agent.client.AgentClient;
import com.civix.agent.client.AgentWire.ProfileFieldsResponse;
import com.civix.citizen.domain.ProfileFieldDefinition;
import com.civix.citizen.domain.ProfileFieldType;
import com.civix.citizen.domain.Sensitivity;
import com.civix.citizen.repository.ProfileFieldDefinitionRepository;
import com.civix.services.service.JurisdictionCatalog;

/** The profile field catalog, synced from the agent registry. */
@Service
public class ProfileFieldCatalogService {

    private static final Logger log = LoggerFactory.getLogger(ProfileFieldCatalogService.class);
    private static final Pattern KEY = Pattern.compile("^[a-z0-9_]{1,64}$");

    private final ProfileFieldDefinitionRepository definitions;
    private final AgentClient agent;
    private final Clock clock;

    public ProfileFieldCatalogService(ProfileFieldDefinitionRepository definitions, AgentClient agent, Clock clock) {
        this.definitions = definitions;
        this.agent = agent;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<ProfileFieldDefinition> all() {
        return definitions.findAllByOrderByDisplayOrderAsc();
    }

    @Transactional(readOnly = true)
    public Map<String, ProfileFieldDefinition> byKey() {
        Map<String, ProfileFieldDefinition> map = new LinkedHashMap<>();
        all().forEach(d -> map.put(d.getFieldKey(), d));
        return map;
    }

    public static FieldSpec spec(ProfileFieldDefinition def) {
        return FieldSpec.from(def.getFieldKey(), def.getFieldType(), def.getDefinition(), JurisdictionCatalog.CODES);
    }

    /** Options for display: explicit options, or jurisdiction codes for {@code optionsFrom: jurisdictions}. */
    public static List<String> options(ProfileFieldDefinition def) {
        return def.getFieldType() == ProfileFieldType.ENUM ? spec(def).options() : null;
    }

    /**
     * Pulls the catalog from the agent and upserts it. Definitions no longer present are removed (stored values
     * for such keys are kept but no longer shown).
     *
     * @return the list of per-field errors (empty on full success)
     */
    @Transactional
    public List<String> sync() {
        ProfileFieldsResponse response = agent.fetchProfileFields();
        if (response == null || response.fields() == null) {
            return List.of("profile field catalog response was empty");
        }
        String version = response.version() == null ? "unknown" : truncate(response.version(), 32);
        Instant now = clock.instant();
        List<String> errors = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        Map<String, ProfileFieldDefinition> existing = new LinkedHashMap<>();
        definitions.findAll().forEach(d -> existing.put(d.getFieldKey(), d));
        int order = 0;
        for (Map<String, Object> raw : response.fields()) {
            order++;
            String key = str(raw.get("key"));
            try {
                if (key == null || !KEY.matcher(key).matches()) {
                    throw new IllegalArgumentException("invalid key");
                }
                if (!seen.add(key)) {
                    throw new IllegalArgumentException("duplicate key");
                }
                ProfileFieldType type = ProfileFieldType.valueOf(required(raw, "type"));
                Sensitivity sensitivity = Sensitivity.valueOf(required(raw, "sensitivity"));
                String label = truncate(required(raw, "label"), 120);
                String group = truncate(required(raw, "group"), 32);
                String purpose = truncate(required(raw, "purpose"), 500);
                boolean core = Boolean.TRUE.equals(raw.get("core"));
                ProfileFieldDefinition def = existing.getOrDefault(key, new ProfileFieldDefinition(key));
                def.update(label, type, group, purpose, sensitivity, core, order, new LinkedHashMap<>(raw), version, now);
                definitions.save(def);
            } catch (IllegalArgumentException e) {
                errors.add("profile field " + (key == null ? "#" + order : key) + ": " + e.getMessage());
            }
        }
        for (ProfileFieldDefinition def : existing.values()) {
            if (!seen.contains(def.getFieldKey())) {
                definitions.delete(def);
            }
        }
        log.info("Profile field catalog {} synced: {} field(s), {} error(s)", version, seen.size(), errors.size());
        return errors;
    }

    private static String required(Map<String, Object> raw, String key) {
        String value = str(raw.get(key));
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("missing " + key);
        }
        return value;
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
