package com.civix.applications.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.civix.applications.domain.ApplicationStatusHistory;

public interface ApplicationStatusHistoryRepository extends JpaRepository<ApplicationStatusHistory, UUID> {

    List<ApplicationStatusHistory> findByApplicationIdOrderByChangedAtAsc(UUID applicationId);
}
