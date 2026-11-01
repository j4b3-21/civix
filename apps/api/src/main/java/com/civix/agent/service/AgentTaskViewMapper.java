package com.civix.agent.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.civix.agent.domain.AgentTask;
import com.civix.agent.domain.TaskStatus;
import com.civix.agent.dto.AgentTaskDto;
import com.civix.applications.service.ApplicationNames;
import com.civix.approvals.dto.ApprovalDto;
import com.civix.approvals.service.ApprovalService;

@Component
public class AgentTaskViewMapper {

    private final ApplicationNames names;
    private final ApprovalService approvals;

    public AgentTaskViewMapper(ApplicationNames names, ApprovalService approvals) {
        this.names = names;
        this.approvals = approvals;
    }

    @Transactional(readOnly = true)
    public AgentTaskDto toDto(AgentTask t) {
        return toDto(t, false);
    }

    @Transactional(readOnly = true)
    public AgentTaskDto toDto(AgentTask t, boolean includeUser) {
        ApprovalDto pending = t.getStatus() == TaskStatus.WAITING_APPROVAL ? approvals.pendingForTask(t.getId()).orElse(null) : null;
        AgentTaskDto.TaskError error = t.getErrorCode() == null ? null
                : new AgentTaskDto.TaskError(t.getErrorCode(), t.getErrorMessage() == null ? "" : t.getErrorMessage());
        return new AgentTaskDto(t.getId(), t.getApplicationId(), names.serviceName(t.getApplicationId()), t.getStatus().name(),
                t.getCurrentStep(), t.getCurrentAgent(), t.getCreatedAt(), t.getStartedAt(), t.getUpdatedAt(),
                t.getCompletedAt(), t.getRecoveryAttempts(), pending,
                t.getStatus() == TaskStatus.WAITING_CITIZEN ? t.getPendingCitizenInput() : null,
                t.getLastObservation(), error, t.getLastEventSequence(), includeUser ? t.getUserId() : null);
    }
}
