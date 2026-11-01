package com.civix.applications.service;

import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.civix.agent.domain.AgentTask;
import com.civix.agent.repository.AgentTaskRepository;
import com.civix.applications.domain.Application;
import com.civix.applications.dto.ApplicationDto;
import com.civix.common.api.ApiException;
import com.civix.services.domain.ServiceDefinition;
import com.civix.services.repository.ServiceDefinitionRepository;
import com.civix.services.service.ServiceViewMapper;

@Component
public class ApplicationViewMapper {

    private final ServiceDefinitionRepository services;
    private final ServiceViewMapper serviceViews;
    private final AgentTaskRepository tasks;

    public ApplicationViewMapper(ServiceDefinitionRepository services, ServiceViewMapper serviceViews, AgentTaskRepository tasks) {
        this.services = services;
        this.serviceViews = serviceViews;
        this.tasks = tasks;
    }

    @Transactional(readOnly = true)
    public ApplicationDto toDto(Application a) {
        ServiceDefinition s = services.findById(a.getServiceId()).orElseThrow(() -> ApiException.notFound("service"));
        UUID latestTask = tasks.findFirstByApplicationIdOrderByCreatedAtDesc(a.getId()).map(AgentTask::getId).orElse(null);
        return new ApplicationDto(a.getId(), serviceViews.summary(s, a.getUserId()), a.getStatus().name(), a.getDataVersion(),
                a.getReferenceNumber(), a.getEligibilityOutcome() == null ? null : a.getEligibilityOutcome().name(),
                a.getCreatedAt(), a.getUpdatedAt(), a.getLastActivityAt(), a.getSubmittedAt(), a.getRequiredAction(),
                latestTask, a.isTestEnvironment() || s.isTestService());
    }
}
