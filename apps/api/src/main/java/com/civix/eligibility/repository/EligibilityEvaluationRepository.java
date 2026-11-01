package com.civix.eligibility.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.civix.eligibility.domain.EligibilityEvaluation;

public interface EligibilityEvaluationRepository extends JpaRepository<EligibilityEvaluation, UUID> {

    Optional<EligibilityEvaluation> findFirstByUserIdAndServiceIdOrderByEvaluatedAtDesc(UUID userId, UUID serviceId);

    /** Latest evaluation per service for the user, restricted to the given services. */
    @Query("""
            select e from EligibilityEvaluation e
            where e.userId = :userId and e.serviceId in :serviceIds
              and e.evaluatedAt = (select max(e2.evaluatedAt) from EligibilityEvaluation e2
                                   where e2.userId = e.userId and e2.serviceId = e.serviceId)
            """)
    List<EligibilityEvaluation> findLatestForServices(@Param("userId") UUID userId,
                                                      @Param("serviceIds") Collection<UUID> serviceIds);

    /** Latest evaluation per service for the user (all services). */
    @Query("""
            select e from EligibilityEvaluation e
            where e.userId = :userId
              and e.evaluatedAt = (select max(e2.evaluatedAt) from EligibilityEvaluation e2
                                   where e2.userId = e.userId and e2.serviceId = e.serviceId)
            """)
    List<EligibilityEvaluation> findLatestPerService(@Param("userId") UUID userId);

    @Modifying(flushAutomatically = true)
    @Query("delete from EligibilityEvaluation e where e.userId = :userId")
    int deleteAllForUser(@Param("userId") UUID userId);
}
