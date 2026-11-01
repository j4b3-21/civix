package com.civix.common.json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.stereotype.Component;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Thin wrapper around the application's Jackson 3 {@link ObjectMapper} for the few places that need explicit
 * JSON conversion (scalar jsonb columns, typed views of agent payloads, canonical hashing).
 */
@Component
public class JsonCodec {

    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {
    };

    private final ObjectMapper mapper;

    public JsonCodec(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public String write(Object value) {
        return mapper.writeValueAsString(value);
    }

    /** Serialises a nullable JSON value for a scalar jsonb column; {@code null} stays SQL NULL. */
    public String writeNullable(Object value) {
        return value == null ? null : mapper.writeValueAsString(value);
    }

    public Object readValue(String json) {
        if (json == null) {
            return null;
        }
        return mapper.readValue(json, Object.class);
    }

    public <T> T convert(Object value, Class<T> type) {
        return mapper.convertValue(value, type);
    }

    public <T> T convert(Object value, TypeReference<T> type) {
        return mapper.convertValue(value, type);
    }

    public Map<String, Object> toMap(Object value) {
        if (value == null) {
            return null;
        }
        return mapper.convertValue(value, MAP);
    }

    /**
     * Canonical JSON (object keys sorted recursively, compact) used for content hashing. Stable across runs
     * for the same logical document.
     */
    public String canonical(Object value) {
        return mapper.writeValueAsString(canonicalize(value));
    }

    private static Object canonicalize(Object value) {
        if (value instanceof Map<?, ?> map) {
            TreeMap<String, Object> sorted = new TreeMap<>();
            map.forEach((k, v) -> sorted.put(String.valueOf(k), canonicalize(v)));
            return sorted;
        }
        if (value instanceof List<?> list) {
            List<Object> out = new ArrayList<>(list.size());
            list.forEach(v -> out.add(canonicalize(v)));
            return out;
        }
        return value;
    }

    /** Shallow-copies a map into a mutable {@link LinkedHashMap} ({@code null} → empty). */
    public static Map<String, Object> mutableCopy(Map<String, ?> source) {
        Map<String, Object> copy = new LinkedHashMap<>();
        if (source != null) {
            copy.putAll(source);
        }
        return copy;
    }
}
