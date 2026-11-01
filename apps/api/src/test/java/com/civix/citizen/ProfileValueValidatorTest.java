package com.civix.citizen;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.civix.citizen.domain.ProfileFieldType;
import com.civix.citizen.service.FieldSpec;
import com.civix.citizen.service.ProfileValueValidator;

class ProfileValueValidatorTest {

    @Test
    void integerWithBounds() {
        FieldSpec income = FieldSpec.from("annual_family_income", ProfileFieldType.INTEGER, Map.of("min", 0, "max", 1_000_000_000), List.of());
        assertThat(ProfileValueValidator.validate(income, 240000).isValid()).isTrue();
        assertThat(ProfileValueValidator.validate(income, -5).isValid()).isFalse();
        assertThat(ProfileValueValidator.validate(income, "abc").isValid()).isFalse();
    }

    @Test
    void enumFromJurisdictions() {
        FieldSpec state = FieldSpec.from("state_code", ProfileFieldType.ENUM, Map.of("optionsFrom", "jurisdictions"), List.of("IN-KA", "IN-HR"));
        assertThat(ProfileValueValidator.validate(state, "IN-KA").isValid()).isTrue();
        assertThat(ProfileValueValidator.validate(state, "IN-XX").isValid()).isFalse();
    }

    @Test
    void patternAndDates() {
        FieldSpec mobile = FieldSpec.from("mobile_number", ProfileFieldType.STRING, Map.of("pattern", "^[6-9][0-9]{9}$"), List.of());
        assertThat(ProfileValueValidator.validate(mobile, "9876543210").isValid()).isTrue();
        assertThat(ProfileValueValidator.validate(mobile, "12345").isValid()).isFalse();
        FieldSpec dob = FieldSpec.from("date_of_birth", ProfileFieldType.DATE, Map.of(), List.of());
        assertThat(ProfileValueValidator.validate(dob, "2005-08-14").isValid()).isTrue();
        assertThat(ProfileValueValidator.validate(dob, "14/08/2005").isValid()).isFalse();
    }

    @Test
    void nullIsNotAValue() {
        FieldSpec name = FieldSpec.from("full_name", ProfileFieldType.STRING, Map.of(), List.of());
        assertThat(ProfileValueValidator.validate(name, null).isValid()).isFalse();
        assertThat(ProfileValueValidator.validate(name, "  ").isValid()).isFalse();
    }
}
