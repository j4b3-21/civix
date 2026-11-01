package com.civix.citizen.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.civix.citizen.domain.CitizenProfile;

public interface CitizenProfileRepository extends JpaRepository<CitizenProfile, UUID> {

    Optional<CitizenProfile> findByUserId(UUID userId);
}
