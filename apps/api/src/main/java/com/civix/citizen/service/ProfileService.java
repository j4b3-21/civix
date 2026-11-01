package com.civix.citizen.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.civix.audit.domain.AuditCategory;
import com.civix.audit.service.AuditEntry;
import com.civix.audit.service.AuditService;
import com.civix.citizen.domain.ChangedBy;
import com.civix.citizen.domain.CitizenProfile;
import com.civix.citizen.domain.FieldSource;
import com.civix.citizen.domain.ProfileField;
import com.civix.citizen.domain.ProfileFieldDefinition;
import com.civix.citizen.domain.ProfileFieldHistory;
import com.civix.citizen.domain.ProfileFieldStatus;
import com.civix.citizen.dto.CompletenessDto;
import com.civix.citizen.dto.ProfileDto;
import com.civix.citizen.dto.ProfileFieldDto;
import com.civix.citizen.dto.UpdateProfileRequest;
import com.civix.citizen.repository.CitizenProfileRepository;
import com.civix.citizen.repository.ProfileFieldHistoryRepository;
import com.civix.citizen.repository.ProfileFieldRepository;
import com.civix.common.api.ApiException;
import com.civix.common.json.JsonCodec;
import com.civix.consent.domain.ConsentPurpose;
import com.civix.consent.service.ConsentService;

/**
 * Citizen profile with per-field provenance. Values only enter the profile through the citizen (PUT /profile)
 * or through the citizen's review of an extraction; {@code VERIFIED} is reserved for verification adapters.
 */
@Service
public class ProfileService {

    private final CitizenProfileRepository profiles;
    private final ProfileFieldRepository fields;
    private final ProfileFieldHistoryRepository history;
    private final ProfileFieldCatalogService catalog;
    private final ConsentService consents;
    private final AuditService audit;
    private final ApplicationEventPublisher events;
    private final JsonCodec json;
    private final Clock clock;

    public ProfileService(CitizenProfileRepository profiles, ProfileFieldRepository fields,
                          ProfileFieldHistoryRepository history, ProfileFieldCatalogService catalog,
                          ConsentService consents, AuditService audit, ApplicationEventPublisher events,
                          JsonCodec json, Clock clock) {
        this.profiles = profiles;
        this.fields = fields;
        this.history = history;
        this.catalog = catalog;
        this.consents = consents;
        this.audit = audit;
        this.events = events;
        this.json = json;
        this.clock = clock;
    }

    @Transactional
    public ProfileDto get(UUID userId) {
        CitizenProfile profile = profileOf(userId);
        List<ProfileFieldDefinition> defs = catalog.all();
        Map<String, ProfileField> values = valuesByKey(userId);
        List<ProfileFieldDto> dtos = new ArrayList<>(defs.size());
        for (ProfileFieldDefinition def : defs) {
            dtos.add(toDto(def, values.get(def.getFieldKey())));
        }
        return new ProfileDto(userId, profile.getUpdatedAt(), completeness(defs, values), dtos);
    }

    @Transactional(readOnly = true)
    public CompletenessDto completeness(UUID userId) {
        return completeness(catalog.all(), valuesByKey(userId));
    }

    @Transactional
    public ProfileDto update(UUID userId, UpdateProfileRequest request) {
        consents.require(userId, ConsentPurpose.PROFILE_PROCESSING);
        Map<String, ProfileFieldDefinition> defs = catalog.byKey();
        if (defs.isEmpty()) {
            throw new ApiException("PROFILE_CATALOG_UNAVAILABLE", HttpStatus.SERVICE_UNAVAILABLE,
                    "The profile field catalog has not been loaded yet. Please try again shortly.");
        }

        // 1. validate everything before touching state
        List<ApiException.FieldError> errors = new ArrayList<>();
        Map<String, Object> toSet = new LinkedHashMap<>();
        Set<String> toClear = new HashSet<>();
        Set<String> seen = new HashSet<>();
        List<UpdateProfileRequest.FieldValueInput> inputs = request.fields() == null ? List.of() : request.fields();
        for (int i = 0; i < inputs.size(); i++) {
            UpdateProfileRequest.FieldValueInput input = inputs.get(i);
            String path = "fields[" + i + "]";
            if (!seen.add(input.key())) {
                errors.add(new ApiException.FieldError(path + ".key", "is listed more than once"));
                continue;
            }
            ProfileFieldDefinition def = defs.get(input.key());
            if (def == null) {
                errors.add(new ApiException.FieldError(path + ".key", "is not a known profile field"));
                continue;
            }
            if (input.value() == null) {
                toClear.add(input.key());
                continue;
            }
            ProfileValueValidator.Result result = ProfileValueValidator.validate(ProfileFieldCatalogService.spec(def), input.value());
            if (result.isValid()) {
                toSet.put(input.key(), result.value());
            } else {
                errors.add(new ApiException.FieldError(path + ".value", result.error()));
            }
        }
        Map<String, ProfileField> existing = valuesByKey(userId);
        List<String> confirmKeys = request.confirmKeys() == null ? List.of() : request.confirmKeys();
        for (int i = 0; i < confirmKeys.size(); i++) {
            String key = confirmKeys.get(i);
            String path = "confirmKeys[" + i + "]";
            if (!defs.containsKey(key)) {
                errors.add(new ApiException.FieldError(path, "is not a known profile field"));
            } else if (toClear.contains(key) || (!toSet.containsKey(key) && !existing.containsKey(key))) {
                errors.add(new ApiException.FieldError(path, "has no value to confirm"));
            }
        }
        if (!errors.isEmpty()) {
            throw ApiException.validation(errors);
        }

        // 2. apply
        Instant now = clock.instant();
        List<String> changedKeys = new ArrayList<>();
        for (String key : toClear) {
            ProfileField field = existing.remove(key);
            if (field != null) {
                history.save(new ProfileFieldHistory(userId, key, field.getStatus(), ProfileFieldStatus.MISSING,
                        null, null, ChangedBy.USER, now));
                fields.delete(field);
                changedKeys.add(key);
            }
        }
        for (Map.Entry<String, Object> e : toSet.entrySet()) {
            String valueJson = json.write(e.getValue());
            ProfileField field = existing.get(e.getKey());
            if (field != null && sameValue(field.getValueJson(), valueJson)) {
                continue;
            }
            ProfileFieldStatus old = field == null ? null : field.getStatus();
            if (field == null) {
                field = new ProfileField(userId, e.getKey(), now);
                existing.put(e.getKey(), field);
            }
            field.set(valueJson, ProfileFieldStatus.PROVIDED, FieldSource.USER_INPUT, null, null, now);
            fields.save(field);
            history.save(new ProfileFieldHistory(userId, e.getKey(), old, ProfileFieldStatus.PROVIDED,
                    FieldSource.USER_INPUT, null, ChangedBy.USER, now));
            changedKeys.add(e.getKey());
        }
        for (String key : confirmKeys) {
            ProfileField field = existing.get(key);
            if (field.getStatus() == ProfileFieldStatus.PROVIDED || field.getStatus() == ProfileFieldStatus.EXTRACTED) {
                ProfileFieldStatus old = field.getStatus();
                field.confirm(now);
                fields.save(field);
                history.save(new ProfileFieldHistory(userId, key, old, ProfileFieldStatus.USER_CONFIRMED,
                        field.getSource(), field.getSourceDocumentId(), ChangedBy.USER, now));
                if (!changedKeys.contains(key)) {
                    changedKeys.add(key);
                }
            }
        }

        if (!changedKeys.isEmpty()) {
            profileOf(userId).touch(now);
            audit.record(AuditEntry.of(AuditCategory.PROFILE, "PROFILE_UPDATED").user(userId)
                    .resource("PROFILE", userId)
                    .summary("Updated " + changedKeys.size() + " profile field(s)")
                    .meta("fieldKeys", changedKeys));
            events.publishEvent(new CitizenDataChangedEvent(userId, "PROFILE_UPDATED"));
        }
        return get(userId);
    }

    /** A value accepted by the citizen while reviewing a document extraction. */
    public record AcceptedValue(String key, Object value, BigDecimal confidence) {
    }

    /**
     * Writes reviewed extraction values: status {@code USER_CONFIRMED}, source {@code DOCUMENT_EXTRACTION}.
     *
     * @return the keys that changed
     */
    @Transactional
    public List<String> applyReviewedDocumentValues(UUID userId, UUID documentId, List<AcceptedValue> accepted,
                                                    String errorPathPrefix) {
        if (accepted.isEmpty()) {
            return List.of();
        }
        consents.require(userId, ConsentPurpose.PROFILE_PROCESSING);
        Map<String, ProfileFieldDefinition> defs = catalog.byKey();
        List<ApiException.FieldError> errors = new ArrayList<>();
        Map<String, Object> normalized = new LinkedHashMap<>();
        for (int i = 0; i < accepted.size(); i++) {
            AcceptedValue a = accepted.get(i);
            String path = errorPathPrefix + "[" + i + "]";
            ProfileFieldDefinition def = defs.get(a.key());
            if (def == null) {
                errors.add(new ApiException.FieldError(path + ".key", "is not a known profile field"));
                continue;
            }
            if (a.value() == null) {
                errors.add(new ApiException.FieldError(path + ".value", "must not be null"));
                continue;
            }
            ProfileValueValidator.Result r = ProfileValueValidator.validate(ProfileFieldCatalogService.spec(def), a.value());
            if (r.isValid()) {
                normalized.put(a.key(), r.value());
            } else {
                errors.add(new ApiException.FieldError(path + ".value", r.error()));
            }
        }
        if (!errors.isEmpty()) {
            throw ApiException.validation(errors);
        }
        Instant now = clock.instant();
        Map<String, ProfileField> existing = valuesByKey(userId);
        Map<String, BigDecimal> confidences = new HashMap<>();
        accepted.forEach(a -> confidences.put(a.key(), a.confidence()));
        List<String> changed = new ArrayList<>();
        for (Map.Entry<String, Object> e : normalized.entrySet()) {
            ProfileField field = existing.get(e.getKey());
            ProfileFieldStatus old = field == null ? null : field.getStatus();
            if (field == null) {
                field = new ProfileField(userId, e.getKey(), now);
            }
            field.set(json.write(e.getValue()), ProfileFieldStatus.USER_CONFIRMED, FieldSource.DOCUMENT_EXTRACTION,
                    documentId, clampConfidence(confidences.get(e.getKey())), now);
            fields.save(field);
            history.save(new ProfileFieldHistory(userId, e.getKey(), old, ProfileFieldStatus.USER_CONFIRMED,
                    FieldSource.DOCUMENT_EXTRACTION, documentId, ChangedBy.USER, now));
            changed.add(e.getKey());
        }
        profileOf(userId).touch(now);
        audit.record(AuditEntry.of(AuditCategory.PROFILE, "PROFILE_UPDATED_FROM_DOCUMENT").user(userId)
                .resource("DOCUMENT", documentId)
                .summary("Confirmed " + changed.size() + " profile field(s) from a reviewed document")
                .meta("fieldKeys", changed));
        events.publishEvent(new CitizenDataChangedEvent(userId, "PROFILE_UPDATED"));
        return changed;
    }

    /** Called when a document is deleted: values stay, the provenance link is removed. */
    @Transactional
    public int detachDocument(UUID documentId) {
        return fields.clearSourceDocument(documentId);
    }

    /** Privacy deletion of the profile values and their provenance history. */
    @Transactional
    public int deleteAll(UUID userId) {
        int deleted = fields.deleteAllForUser(userId);
        history.deleteAllForUser(userId);
        profiles.findByUserId(userId).ifPresent(p -> p.touch(clock.instant()));
        return deleted;
    }

    // ---- helpers -----------------------------------------------------------------------------------

    private CitizenProfile profileOf(UUID userId) {
        return profiles.findByUserId(userId)
                .orElseGet(() -> profiles.save(new CitizenProfile(userId, clock.instant())));
    }

    private Map<String, ProfileField> valuesByKey(UUID userId) {
        Map<String, ProfileField> map = new HashMap<>();
        for (ProfileField f : fields.findByUserId(userId)) {
            map.put(f.getFieldKey(), f);
        }
        return map;
    }

    private boolean sameValue(String storedJson, String newJson) {
        if (storedJson == null) {
            return false;
        }
        return json.write(json.readValue(storedJson)).equals(newJson);
    }

    private ProfileFieldDto toDto(ProfileFieldDefinition def, ProfileField value) {
        boolean has = value != null && value.getStatus() != ProfileFieldStatus.MISSING;
        String unit = def.getDefinition() == null || def.getDefinition().get("unit") == null ? null
                : def.getDefinition().get("unit").toString();
        return new ProfileFieldDto(def.getFieldKey(), def.getLabel(), def.getFieldType(), def.getFieldGroup(),
                def.getPurpose(), def.getSensitivity(), def.isCore(), ProfileFieldCatalogService.options(def), unit,
                has ? json.readValue(value.getValueJson()) : null,
                has ? value.getStatus() : ProfileFieldStatus.MISSING,
                has ? value.getSource() : null,
                has ? value.getSourceDocumentId() : null,
                has ? value.getConfidence() : null,
                has ? value.getUpdatedAt() : null);
    }

    private static CompletenessDto completeness(List<ProfileFieldDefinition> defs, Map<String, ProfileField> values) {
        List<String> missing = new ArrayList<>();
        int core = 0;
        int filled = 0;
        for (ProfileFieldDefinition def : defs) {
            if (!def.isCore()) {
                continue;
            }
            core++;
            ProfileField f = values.get(def.getFieldKey());
            if (f != null && f.getStatus() != ProfileFieldStatus.MISSING) {
                filled++;
            } else {
                missing.add(def.getFieldKey());
            }
        }
        int percent = core == 0 ? 0 : (int) Math.round(filled * 100.0 / core);
        return new CompletenessDto(percent, core, filled, missing);
    }

    private static BigDecimal clampConfidence(BigDecimal c) {
        if (c == null) {
            return null;
        }
        if (c.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO;
        }
        if (c.compareTo(BigDecimal.ONE) > 0) {
            return BigDecimal.ONE;
        }
        return c.setScale(3, RoundingMode.HALF_UP);
    }
}
