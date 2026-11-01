package com.civix.services.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.civix.services.domain.ServiceSource;

public interface ServiceSourceRepository extends JpaRepository<ServiceSource, UUID> {

    List<ServiceSource> findByServiceIdOrderByTitleAsc(UUID serviceId);

    @Modifying(flushAutomatically = true)
    @Query("delete from ServiceSource s where s.serviceId = :serviceId")
    int deleteAllForService(@Param("serviceId") UUID serviceId);
}
