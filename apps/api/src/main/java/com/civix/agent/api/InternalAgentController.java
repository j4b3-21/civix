package com.civix.agent.api;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.civix.agent.domain.AgentTask;
import com.civix.agent.dto.AgentEventsRequest;
import com.civix.agent.repository.AgentTaskRepository;
import com.civix.agent.service.AgentEventIngestService;
import com.civix.applications.domain.Application;
import com.civix.applications.repository.ApplicationRepository;
import com.civix.approvals.dto.InternalApprovalRequest;
import com.civix.approvals.dto.InternalApprovalResponse;
import com.civix.approvals.service.ApprovalService;
import com.civix.citizen.service.CitizenContextService;
import com.civix.common.api.ApiException;
import com.civix.consent.domain.ConsentPurpose;
import com.civix.receipts.dto.InternalReceiptRequest;
import com.civix.receipts.service.ReceiptService;

/** Agent → API callbacks. Secured by the internal bearer token (SecurityConfig order 1); never routed publicly. */
@RestController
@RequestMapping("/internal/v1")
public class InternalAgentController {

    private final AgentEventIngestService ingest;
    private final ApprovalService approvals;
    private final ReceiptService receipts;
    private final AgentTaskRepository tasks;
    private final ApplicationRepository applications;
    private final CitizenContextService contexts;

    public InternalAgentController(AgentEventIngestService ingest, ApprovalService approvals, ReceiptService receipts,
                                   AgentTaskRepository tasks, ApplicationRepository applications, CitizenContextService contexts) {
        this.ingest = ingest;
        this.approvals = approvals;
        this.receipts = receipts;
        this.tasks = tasks;
        this.applications = applications;
        this.contexts = contexts;
    }

    @PostMapping("/agent-events")
    public AgentEventIngestService.IngestResult events(@Valid @RequestBody AgentEventsRequest request) {
        return ingest.ingest(request.events());
    }

    @PostMapping("/approvals")
    public ResponseEntity<InternalApprovalResponse> createApproval(@Valid @RequestBody InternalApprovalRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(approvals.createFromAgent(request));
    }

    @PostMapping("/receipts")
    public ResponseEntity<Map<String, UUID>> createReceipt(@Valid @RequestBody InternalReceiptRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("receiptId", receipts.createFromAgent(request)));
    }

    /** Current data version and consented context, so the agent can detect changes before consequential steps. */
    @GetMapping("/tasks/{taskId}/context")
    @Transactional(readOnly = true)
    public Map<String, Object> context(@PathVariable UUID taskId) {
        AgentTask task = tasks.findById(taskId).orElseThrow(() -> ApiException.notFound("task"));
        Application app = applications.findById(task.getApplicationId()).orElseThrow(() -> ApiException.notFound("application"));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("dataVersion", app.getDataVersion());
        out.put("applicationStatus", app.getStatus().name());
        out.put("citizenContext", contexts.build(task.getUserId(), ConsentPurpose.AGENT_EXECUTION));
        out.put("documents", List.of());
        return out;
    }
}
