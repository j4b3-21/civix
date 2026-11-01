package com.civix.receipts.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.civix.receipts.domain.ApplicationReceipt;

public interface ApplicationReceiptRepository extends JpaRepository<ApplicationReceipt, UUID> {

    List<ApplicationReceipt> findByApplicationIdOrderByVerifiedAtDesc(UUID applicationId);

    Optional<ApplicationReceipt> findByIdAndApplicationId(UUID id, UUID applicationId);

    Optional<ApplicationReceipt> findByApplicationIdAndReferenceNumber(UUID applicationId, String referenceNumber);

    long countByApplicationId(UUID applicationId);
}
