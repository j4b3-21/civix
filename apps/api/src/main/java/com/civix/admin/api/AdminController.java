package com.civix.admin.api;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.civix.agent.domain.AgentTask;
import com.civix.agent.domain.TaskStatus;
import com.civix.agent.dto.AgentTaskDto;
import com.civix.agent.dto.ExecutionEventDto;
import com.civix.agent.repository.AgentTaskRepository;
import com.civix.agent.service.AgentTaskService;
import com.civix.agent.service.AgentTaskViewMapper;
import com.civix.audit.domain.ActorType;
import com.civix.audit.domain.AuditCategory;
import com.civix.audit.service.AuditEntry;
import com.civix.audit.service.AuditService;
import com.civix.common.api.ApiException;
import com.civix.common.api.PageResponse;
import com.civix.common.api.Pagination;
import com.civix.common.security.CurrentUser;
import com.civix.services.dto.RegistrySyncResultDto;
import com.civix.services.dto.ServiceSummaryDto;
import com.civix.services.repository.ServiceDefinitionRepository;
import com.civix.services.service.RegistrySyncService;
import com.civix.services.service.ServiceViewMapper;

/** Operational view (ROLE_ADMIN, enforced in SecurityConfig): failed-task inspection and registry management. */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    private final AgentTaskRepository tasks;
    private final AgentTaskViewMapper taskViews;
    private final AgentTaskService taskService;
    private final ServiceDefinitionRepository services;
    private final ServiceViewMapper serviceViews;
    private final RegistrySyncService registry;
    private final AuditService audit;

    public AdminController(AgentTaskRepository tasks, AgentTaskViewMapper taskViews, AgentTaskService taskService,
                           ServiceDefinitionRepository services, ServiceViewMapper serviceViews, RegistrySyncService registry,
                           AuditService audit) {
        this.tasks = tasks;
        this.taskViews = taskViews;
        this.taskService = taskService;
        this.services = services;
        this.serviceViews = serviceViews;
        this.registry = registry;
        this.audit = audit;
    }

    @GetMapping("/tasks")
    @Transactional(readOnly = true)
    public PageResponse<AgentTaskDto> tasks(@RequestParam(defaultValue = "FAILED") TaskStatus status,
                                            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        Pageable pageable = Pagination.newestFirst(page, size, "updatedAt");
        Page<AgentTask> result = tasks.findByStatus(status, pageable);
        return PageResponse.from(result, t -> taskViews.toDto(t, true));
    }

    @GetMapping("/tasks/{id}/events")
    @Transactional(readOnly = true)
    public List<ExecutionEventDto> taskEvents(@PathVariable UUID id) {
        tasks.findById(id).orElseThrow(() -> ApiException.notFound("task"));
        return taskService.history(id, 0, 500);
    }

    @GetMapping("/services")
    @Transactional(readOnly = true)
    public PageResponse<ServiceSummaryDto> services(@RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return PageResponse.from(services.findByActiveTrue(Pagination.of(page, size, org.springframework.data.domain.Sort.by("code"))),
                s -> serviceViews.summary(s, (com.civix.eligibility.domain.EligibilityEvaluation) null));
    }

    @PostMapping("/registry/sync")
    public RegistrySyncResultDto sync() {
        RegistrySyncResultDto result = registry.syncAll();
        audit.record(AuditEntry.of(AuditCategory.ADMIN, "REGISTRY_SYNC").user(CurrentUser.id())
                .actor(ActorType.ADMIN, CurrentUser.id().toString())
                .summary("Registry sync: " + result.synced() + " synced, " + result.deactivated() + " deactivated"));
        return result;
    }
}
