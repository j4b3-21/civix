package com.civix.services.service;

import java.util.List;
import java.util.Optional;

/**
 * The 36 States and Union Territories of India with ISO 3166-2:IN codes (mirrors service-registry/jurisdictions.yaml).
 */
public final class JurisdictionCatalog {

    public enum Type { STATE, UNION_TERRITORY }

    public record Jurisdiction(String code, String name, Type type) {
    }

    public static final List<Jurisdiction> ALL = List.of(
            new Jurisdiction("IN-AP", "Andhra Pradesh", Type.STATE),
            new Jurisdiction("IN-AR", "Arunachal Pradesh", Type.STATE),
            new Jurisdiction("IN-AS", "Assam", Type.STATE),
            new Jurisdiction("IN-BR", "Bihar", Type.STATE),
            new Jurisdiction("IN-CT", "Chhattisgarh", Type.STATE),
            new Jurisdiction("IN-GA", "Goa", Type.STATE),
            new Jurisdiction("IN-GJ", "Gujarat", Type.STATE),
            new Jurisdiction("IN-HR", "Haryana", Type.STATE),
            new Jurisdiction("IN-HP", "Himachal Pradesh", Type.STATE),
            new Jurisdiction("IN-JH", "Jharkhand", Type.STATE),
            new Jurisdiction("IN-KA", "Karnataka", Type.STATE),
            new Jurisdiction("IN-KL", "Kerala", Type.STATE),
            new Jurisdiction("IN-MP", "Madhya Pradesh", Type.STATE),
            new Jurisdiction("IN-MH", "Maharashtra", Type.STATE),
            new Jurisdiction("IN-MN", "Manipur", Type.STATE),
            new Jurisdiction("IN-ML", "Meghalaya", Type.STATE),
            new Jurisdiction("IN-MZ", "Mizoram", Type.STATE),
            new Jurisdiction("IN-NL", "Nagaland", Type.STATE),
            new Jurisdiction("IN-OR", "Odisha", Type.STATE),
            new Jurisdiction("IN-PB", "Punjab", Type.STATE),
            new Jurisdiction("IN-RJ", "Rajasthan", Type.STATE),
            new Jurisdiction("IN-SK", "Sikkim", Type.STATE),
            new Jurisdiction("IN-TN", "Tamil Nadu", Type.STATE),
            new Jurisdiction("IN-TG", "Telangana", Type.STATE),
            new Jurisdiction("IN-TR", "Tripura", Type.STATE),
            new Jurisdiction("IN-UP", "Uttar Pradesh", Type.STATE),
            new Jurisdiction("IN-UT", "Uttarakhand", Type.STATE),
            new Jurisdiction("IN-WB", "West Bengal", Type.STATE),
            new Jurisdiction("IN-AN", "Andaman and Nicobar Islands", Type.UNION_TERRITORY),
            new Jurisdiction("IN-CH", "Chandigarh", Type.UNION_TERRITORY),
            new Jurisdiction("IN-DH", "Dadra and Nagar Haveli and Daman and Diu", Type.UNION_TERRITORY),
            new Jurisdiction("IN-DL", "Delhi", Type.UNION_TERRITORY),
            new Jurisdiction("IN-JK", "Jammu and Kashmir", Type.UNION_TERRITORY),
            new Jurisdiction("IN-LA", "Ladakh", Type.UNION_TERRITORY),
            new Jurisdiction("IN-LD", "Lakshadweep", Type.UNION_TERRITORY),
            new Jurisdiction("IN-PY", "Puducherry", Type.UNION_TERRITORY));

    public static final List<String> CODES = ALL.stream().map(Jurisdiction::code).toList();

    private JurisdictionCatalog() {
    }

    public static boolean isValid(String code) {
        return code != null && CODES.contains(code);
    }

    public static Optional<Jurisdiction> find(String code) {
        return ALL.stream().filter(j -> j.code().equals(code)).findFirst();
    }
}
