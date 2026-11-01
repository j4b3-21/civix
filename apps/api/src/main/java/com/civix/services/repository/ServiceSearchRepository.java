package com.civix.services.repository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.civix.services.domain.JurisdictionLevel;
import com.civix.services.domain.ServiceCategory;
import com.civix.services.domain.ServiceDefinition;

/**
 * Catalog search over {@code service_definitions}: full-text ({@code search_vector @@ websearch_to_tsquery})
 * with an {@code ILIKE} fallback on name/summary/department, plus category / jurisdiction / state filters.
 * The SQL is assembled from fixed fragments; every user value is a bind parameter.
 */
@Repository
public class ServiceSearchRepository {

    private final EntityManager em;
    private final ServiceDefinitionRepository definitions;

    public ServiceSearchRepository(EntityManager em, ServiceDefinitionRepository definitions) {
        this.em = em;
        this.definitions = definitions;
    }

    public record Criteria(String q, ServiceCategory category, JurisdictionLevel level, String state,
                           boolean includeTest) {
    }

    public record Result(List<ServiceDefinition> items, long total) {
    }

    @Transactional(readOnly = true)
    public Result search(Criteria c, int page, int size) {
        StringBuilder where = new StringBuilder(" WHERE s.active = true");
        Map<String, Object> params = new HashMap<>();
        if (!c.includeTest()) {
            where.append(" AND s.is_test_service = false");
        }
        if (c.category() != null) {
            where.append(" AND s.category = :category");
            params.put("category", c.category().name());
        }
        if (c.level() != null) {
            where.append(" AND s.jurisdiction_level = :level");
            params.put("level", c.level().name());
        }
        if (c.state() != null) {
            where.append(" AND (cardinality(s.state_codes) = 0 OR CAST(:state AS text) = ANY(s.state_codes))");
            params.put("state", c.state());
        }
        String order = " ORDER BY s.name ASC, s.id ASC";
        if (c.q() != null) {
            where.append(" AND (s.search_vector @@ websearch_to_tsquery('english', CAST(:q AS text))"
                    + " OR s.name ILIKE CAST(:like AS text) ESCAPE '\\'"
                    + " OR s.summary ILIKE CAST(:like AS text) ESCAPE '\\'"
                    + " OR s.department ILIKE CAST(:like AS text) ESCAPE '\\')");
            params.put("q", c.q());
            params.put("like", "%" + escapeLike(c.q()) + "%");
            order = " ORDER BY ts_rank(s.search_vector, websearch_to_tsquery('english', CAST(:q AS text))) DESC,"
                    + " s.name ASC, s.id ASC";
        }

        Query count = em.createNativeQuery("SELECT count(*) FROM civix.service_definitions s" + where);
        params.forEach(count::setParameter);
        long total = ((Number) count.getSingleResult()).longValue();
        if (total == 0) {
            return new Result(List.of(), 0);
        }

        Query select = em.createNativeQuery("SELECT CAST(s.id AS text) FROM civix.service_definitions s" + where + order);
        params.forEach(select::setParameter);
        select.setFirstResult(page * size);
        select.setMaxResults(size);
        List<UUID> ids = new ArrayList<>();
        for (Object row : select.getResultList()) {
            ids.add(UUID.fromString(Objects.toString(row)));
        }
        Map<UUID, ServiceDefinition> byId = new HashMap<>();
        definitions.findAllById(ids).forEach(s -> byId.put(s.getId(), s));
        List<ServiceDefinition> ordered = ids.stream().map(byId::get).filter(Objects::nonNull).toList();
        return new Result(ordered, total);
    }

    static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
