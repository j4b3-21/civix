package com.civix.citizen.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.civix.citizen.domain.ProfileField;
import com.civix.citizen.domain.ProfileFieldStatus;
import com.civix.citizen.dto.CitizenContext;
import com.civix.citizen.repository.ProfileFieldRepository;
import com.civix.common.json.JsonCodec;
import com.civix.consent.domain.ConsentPurpose;
import com.civix.consent.service.ConsentService;

/**
 * Builds the {@code CitizenContext} handed to the agent. Profile values are included only if the citizen granted
 * {@code PROFILE_PROCESSING} <em>and</em> the consent for the purpose of the call.
 */
@Service
public class CitizenContextService {

    private final ProfileFieldRepository fields;
    private final ConsentService consents;
    private final JsonCodec json;

    public CitizenContextService(ProfileFieldRepository fields, ConsentService consents, JsonCodec json) {
        this.fields = fields;
        this.consents = consents;
        this.json = json;
    }

    @Transactional(readOnly = true)
    public CitizenContext build(UUID userId, ConsentPurpose purpose) {
        Set<ConsentPurpose> granted = consents.granted(userId);
        Map<String, CitizenContext.FieldValue> values = new LinkedHashMap<>();
        boolean include = granted.contains(ConsentPurpose.PROFILE_PROCESSING) && granted.contains(purpose);
        if (include) {
            for (ProfileField f : fields.findByUserId(userId)) {
                if (f.getStatus() == ProfileFieldStatus.MISSING || f.getValueJson() == null) {
                    continue;
                }
                values.put(f.getFieldKey(), new CitizenContext.FieldValue(
                        json.readValue(f.getValueJson()),
                        f.getStatus().name(),
                        f.getSource() == null ? null : f.getSource().name(),
                        f.getSourceDocumentId(),
                        f.getConfidence() == null ? null : f.getConfidence().doubleValue()));
            }
        }
        List<String> consentNames = granted.stream().map(Enum::name).sorted().toList();
        return new CitizenContext(userId, values, consentNames);
    }
}
