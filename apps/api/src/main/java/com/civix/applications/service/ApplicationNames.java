package com.civix.applications.service;

import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.civix.applications.repository.ApplicationRepository;
import com.civix.services.domain.ServiceDefinition;
import com.civix.services.repository.ServiceDefinitionRepository;

/** Resolves the human-readable service name shown on tasks and approvals. */
@Component
public class ApplicationNames {

    private final ApplicationRepository applications;
    private final ServiceDefinitionRepository services;

    public ApplicationNames(ApplicationRepository applications, ServiceDefinitionRepository services) {
        this.applications = applications;
        this.services = services;
    }

    @Transactional(readOnly = true)
    public String serviceName(UUID applicationId) {
        return applications.findById(applicationId)
                .flatMap(a -> services.findById(a.getServiceId()))
                .map(ServiceDefinition::getName)
                .orElse("Unknown service");
    }
}
