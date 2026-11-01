package com.civix.services.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.civix.services.domain.EligibilityRule;

public interface EligibilityRuleRepository extends JpaRepository<EligibilityRule, UUID> {

    List<EligibilityRule> findByServiceIdOrderByRuleIdAsc(UUID serviceId);

    @Modifying(flushAutomatically = true)
    @Query("delete from EligibilityRule r where r.serviceId = :serviceId")
    int deleteAllForService(@Param("serviceId") UUID serviceId);
}
