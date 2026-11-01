package com.civix.citizen.service;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Validates and normalises a raw JSON value against a profile field definition. Pure (no framework types), so
 * the rules are unit-testable and identical wherever values enter the profile (PUT /profile, document review).
 */
public final class ProfileValueValidator {

    static final int DEFAULT_MAX_STRING_LENGTH = 500;
    private static final Pattern NUMERIC = Pattern.compile("^-?\\d+(\\.\\d+)?$");
    private static final Pattern ISO_DATE = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");

    private ProfileValueValidator() {
    }

    /** Outcome: either a normalised value (String, Long, Double, Boolean or ISO date String) or an error message. */
    public record Result(Object value, String error) {

        static Result ok(Object value) {
            return new Result(value, null);
        }

        static Result error(String message) {
            return new Result(null, message);
        }

        public boolean isValid() {
            return error == null;
        }
    }

    public static Result validate(FieldSpec spec, Object raw) {
        if (raw == null) {
            return Result.error("must not be null (send null only to clear the field)");
        }
        return switch (spec.type()) {
            case STRING -> validateString(spec, raw);
            case NUMBER -> validateNumber(spec, raw, false);
            case INTEGER -> validateNumber(spec, raw, true);
            case BOOLEAN -> validateBoolean(raw);
            case DATE -> validateDate(spec, raw);
            case ENUM -> validateEnum(spec, raw);
        };
    }

    private static Result validateString(FieldSpec spec, Object raw) {
        if (!(raw instanceof String s)) {
            return Result.error("must be a text value");
        }
        String value = s.strip();
        if (value.isEmpty()) {
            return Result.error("must not be blank");
        }
        if (value.chars().anyMatch(c -> Character.isISOControl(c) && c != '\n' && c != '\t')) {
            return Result.error("must not contain control characters");
        }
        int max = spec.maxLength() != null ? spec.maxLength() : DEFAULT_MAX_STRING_LENGTH;
        if (value.length() > max) {
            return Result.error("must be at most " + max + " characters");
        }
        if (spec.minLength() != null && value.length() < spec.minLength()) {
            return Result.error("must be at least " + spec.minLength() + " characters");
        }
        if (spec.pattern() != null) {
            try {
                if (!Pattern.compile(spec.pattern()).matcher(value).matches()) {
                    return Result.error("has an invalid format");
                }
            } catch (PatternSyntaxException e) {
                return Result.error("cannot be validated (invalid field definition)");
            }
        }
        return Result.ok(value);
    }

    private static Result validateNumber(FieldSpec spec, Object raw, boolean integer) {
        BigDecimal number;
        if (raw instanceof Boolean) {
            return Result.error(integer ? "must be a whole number" : "must be a number");
        } else if (raw instanceof Number n) {
            if (n instanceof Double d && (d.isNaN() || d.isInfinite())) {
                return Result.error("must be a finite number");
            }
            if (n instanceof Float f && (f.isNaN() || f.isInfinite())) {
                return Result.error("must be a finite number");
            }
            try {
                number = new BigDecimal(n.toString());
            } catch (NumberFormatException e) {
                return Result.error("must be a number");
            }
        } else if (raw instanceof String s && NUMERIC.matcher(s.strip()).matches()) {
            number = new BigDecimal(s.strip());
        } else {
            return Result.error(integer ? "must be a whole number" : "must be a number");
        }
        if (spec.min() != null && number.compareTo(spec.min()) < 0) {
            return Result.error("must be at least " + spec.min().toPlainString());
        }
        if (spec.max() != null && number.compareTo(spec.max()) > 0) {
            return Result.error("must be at most " + spec.max().toPlainString());
        }
        BigDecimal stripped = number.stripTrailingZeros();
        boolean isIntegral = stripped.scale() <= 0;
        if (integer && !isIntegral) {
            return Result.error("must be a whole number");
        }
        if (isIntegral) {
            BigInteger bi = stripped.toBigIntegerExact();
            if (bi.bitLength() > 63) {
                return Result.error("is out of range");
            }
            return Result.ok(bi.longValue());
        }
        return Result.ok(number.doubleValue());
    }

    private static Result validateBoolean(Object raw) {
        if (raw instanceof Boolean b) {
            return Result.ok(b);
        }
        if (raw instanceof String s) {
            if ("true".equalsIgnoreCase(s.strip())) {
                return Result.ok(Boolean.TRUE);
            }
            if ("false".equalsIgnoreCase(s.strip())) {
                return Result.ok(Boolean.FALSE);
            }
        }
        return Result.error("must be true or false");
    }

    private static Result validateDate(FieldSpec spec, Object raw) {
        if (!(raw instanceof String s) || !ISO_DATE.matcher(s.strip()).matches()) {
            return Result.error("must be a date in YYYY-MM-DD format");
        }
        LocalDate date;
        try {
            date = LocalDate.parse(s.strip());
        } catch (DateTimeParseException e) {
            return Result.error("must be a valid calendar date");
        }
        if (date.getYear() < 1900) {
            return Result.error("must not be before 1900-01-01");
        }
        if (spec.minDate() != null) {
            try {
                if (date.isBefore(LocalDate.parse(spec.minDate()))) {
                    return Result.error("must not be before " + spec.minDate());
                }
            } catch (DateTimeParseException ignored) {
                // non-date bound in the definition: ignore
            }
        }
        if (spec.maxDate() != null) {
            try {
                if (date.isAfter(LocalDate.parse(spec.maxDate()))) {
                    return Result.error("must not be after " + spec.maxDate());
                }
            } catch (DateTimeParseException ignored) {
                // non-date bound in the definition: ignore
            }
        }
        return Result.ok(date.toString());
    }

    private static Result validateEnum(FieldSpec spec, Object raw) {
        if (!(raw instanceof String s)) {
            return Result.error("must be one of the allowed options");
        }
        String value = s.strip();
        if (spec.options() == null || !spec.options().contains(value)) {
            return Result.error("must be one of the allowed options");
        }
        return Result.ok(value);
    }
}
