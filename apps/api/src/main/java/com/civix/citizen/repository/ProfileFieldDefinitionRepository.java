package com.civix.citizen.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.civix.citizen.domain.ProfileFieldDefinition;

public interface ProfileFieldDefinitionRepository extends JpaRepository<ProfileFieldDefinition, String> {

    List<ProfileFieldDefinition> findAllByOrderByDisplayOrderAsc();
}
