package com.civix.applications.api;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.civix.agent.dto.AgentTaskDto;
import com.civix.applications.domain.ApplicationStatus;
import com.civix.applications.dto.ApplicationDto;
import com.civix.applications.dto.CreateApplicationRequest;
import com.civix.applications.service.ApplicationService;
import com.civix.common.api.PageResponse;
import com.civix.common.api.Pagination;
import com.civix.common.security.CurrentUser;
import com.civix.common.web.Images;
import com.civix.receipts.dto.ReceiptDto;
import com.civix.receipts.service.ReceiptService;

@RestController
@RequestMapping("/api/v1/applications")
public class ApplicationController {

    private final ApplicationService applications;
    private final ReceiptService receipts;

    public ApplicationController(ApplicationService applications, ReceiptService receipts) {
        this.applications = applications;
        this.receipts = receipts;
    }

    @PostMapping
    public ResponseEntity<ApplicationDto> create(@Valid @RequestBody CreateApplicationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(applications.create(CurrentUser.id(), request.serviceId()));
    }

    @GetMapping
    public PageResponse<ApplicationDto> list(@RequestParam(required = false) ApplicationStatus status,
                                             @RequestParam(required = false) Integer page,
                                             @RequestParam(required = false) Integer size) {
        return PageResponse.from(applications.list(CurrentUser.id(), status,
                Pagination.newestFirst(page, size, "lastActivityAt")), x -> x);
    }

    @GetMapping("/{id}")
    public ApplicationDto get(@PathVariable UUID id) {
        return applications.get(CurrentUser.id(), id);
    }

    @PostMapping("/{id}/execute")
    public ResponseEntity<AgentTaskDto> execute(@PathVariable UUID id) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(applications.execute(CurrentUser.id(), id));
    }

    @PostMapping("/{id}/cancel")
    public ApplicationDto cancel(@PathVariable UUID id) {
        return applications.cancel(CurrentUser.id(), id);
    }

    @GetMapping("/{id}/receipts")
    public List<ReceiptDto> receipts(@PathVariable UUID id) {
        return receipts.list(CurrentUser.id(), id);
    }

    @GetMapping("/{id}/receipts/{receiptId}/artifact")
    public ResponseEntity<InputStreamResource> artifact(@PathVariable UUID id, @PathVariable UUID receiptId) {
        return Images.png(receipts.artifact(CurrentUser.id(), id, receiptId));
    }
}
