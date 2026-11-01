package com.civix.approvals.api;

import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.civix.approvals.domain.ApprovalStatus;
import com.civix.approvals.dto.ApprovalDto;
import com.civix.approvals.dto.DecisionRequest;
import com.civix.approvals.service.ApprovalService;
import com.civix.common.api.PageResponse;
import com.civix.common.api.Pagination;
import com.civix.common.security.CurrentUser;

@RestController
@RequestMapping("/api/v1/approvals")
public class ApprovalController {

    private final ApprovalService approvals;

    public ApprovalController(ApprovalService approvals) {
        this.approvals = approvals;
    }

    @GetMapping
    public PageResponse<ApprovalDto> list(@RequestParam(required = false) ApprovalStatus status,
                                          @RequestParam(required = false) Integer page,
                                          @RequestParam(required = false) Integer size) {
        return PageResponse.from(approvals.list(CurrentUser.id(), status, Pagination.newestFirst(page, size, "createdAt")), x -> x);
    }

    @GetMapping("/{id}")
    public ApprovalDto get(@PathVariable UUID id) {
        return approvals.get(CurrentUser.id(), id);
    }

    @PostMapping("/{id}/decision")
    public ApprovalDto decide(@PathVariable UUID id, @Valid @RequestBody DecisionRequest request) {
        return approvals.decide(CurrentUser.id(), id, request);
    }
}
