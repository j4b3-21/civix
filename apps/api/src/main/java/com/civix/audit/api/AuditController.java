package com.civix.audit.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.civix.audit.domain.AuditCategory;
import com.civix.audit.dto.AuditEventDto;
import com.civix.audit.service.AuditService;
import com.civix.common.api.PageResponse;
import com.civix.common.api.Pagination;
import com.civix.common.security.CurrentUser;

@RestController
@RequestMapping("/api/v1/audit")
public class AuditController {

    private final AuditService audit;

    public AuditController(AuditService audit) {
        this.audit = audit;
    }

    @GetMapping("/events")
    public PageResponse<AuditEventDto> events(@RequestParam(required = false) Integer page,
                                              @RequestParam(required = false) Integer size,
                                              @RequestParam(required = false) AuditCategory category) {
        return PageResponse.from(audit.forUser(CurrentUser.id(), category,
                Pagination.newestFirst(page, size, "occurredAt")), x -> x);
    }
}
