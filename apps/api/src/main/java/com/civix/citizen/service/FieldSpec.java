package com.civix.citizen.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import com.civix.citizen.domain.ProfileFieldType;

/**
 * Validation-relevant part of a profile field definition. Built from the catalog JSON
 * ({@code options}, {@code optionsFrom}, {@code unit}, {@code min}, {@code max}, {@code pattern},
 * {@code minLength}, {@code maxLength}).
 */
public record FieldSpec(String key, ProfileFieldType type, List<String> options, String unit, BigDecimal min,
                        BigDecimal max, String minDate, String maxDate, String pattern, Integer minLength,
                        Integer maxLength) {

    public static FieldSpec from(String key, ProfileFieldType type, Map<String, Object> definition,
                                 List<String> jurisdictionCodes) {
        Map<String, Object> d = definition == null ? Map.of() : definition;
        List<String> options = stringList(d.get("options"));
        if ((options == null || options.isEmpty()) && "jurisdictions".equals(d.get("optionsFrom"))) {
            options = jurisdictionCodes;
        }
        Object min = d.get("min");
        Object max = d.get("max");
        return new FieldSpec(key, type, options, string(d.get("unit")),
                decimal(min), decimal(max),
                type == ProfileFieldType.DATE ? string(min) : null,
                type == ProfileFieldType.DATE ? string(max) : null,
                string(d.get("pattern")), integer(d.get("minLength")), integer(d.get("maxLength")));
    }

    static List<String> stringList(Object o) {
        if (o instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return null;
    }

    private static String string(Object o) {
        return o == null ? null : o.toString();
    }

    private static BigDecimal decimal(Object o) {
        if (o instanceof Number n && !(o instanceof Double d && (d.isNaN() || d.isInfinite()))) {
            try {
                return new BigDecimal(n.toString());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private static Integer integer(Object o) {
        return o instanceof Number n ? n.intValue() : null;
    }
}
