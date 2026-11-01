package com.civix.services.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.civix.services.domain.ServiceDefinition;

public interface ServiceDefinitionRepository extends JpaRepository<ServiceDefinition, UUID> {

    Optional<ServiceDefinition> findByCodeAndDefinitionVersion(String code, int definitionVersion);

    List<ServiceDefinition> findByCode(String code);

    List<ServiceDefinition> findByActiveTrue();

    @Query("""
            select s from ServiceDefinition s
            where s.active = true and (:includeTest = true or s.testService = false)
            order by s.name
            """)
    List<ServiceDefinition> findVisible(@Param("includeTest") boolean includeTest);

    @Query("""
            select s from ServiceDefinition s
            where s.id = :id and s.active = true and (:includeTest = true or s.testService = false)
            """)
    Optional<ServiceDefinition> findVisibleById(@Param("id") UUID id, @Param("includeTest") boolean includeTest);

    @Query("""
            select s from ServiceDefinition s
            where s.code in :codes and s.active = true and (:includeTest = true or s.testService = false)
            """)
    List<ServiceDefinition> findVisibleByCodes(@Param("codes") Collection<String> codes,
                                               @Param("includeTest") boolean includeTest);

    @Query("""
            select s.category, count(s) from ServiceDefinition s
            where s.active = true and (:includeTest = true or s.testService = false)
            group by s.category
            """)
    List<Object[]> countByCategory(@Param("includeTest") boolean includeTest);

    /** Admin view: all active definitions including drafts and test services. */
    Page<ServiceDefinition> findByActiveTrue(Pageable pageable);
}
