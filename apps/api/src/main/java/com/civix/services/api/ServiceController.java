package com.civix.services.api;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.civix.common.api.PageResponse;
import com.civix.common.security.CurrentUser;
import com.civix.eligibility.dto.EligibilityResultDto;
import com.civix.eligibility.service.EligibilityService;
import com.civix.services.domain.JurisdictionLevel;
import com.civix.services.domain.ServiceCategory;
import com.civix.services.dto.CategoryCountDto;
import com.civix.services.dto.DiscoverRequest;
import com.civix.services.dto.DiscoveryResponseDto;
import com.civix.services.dto.JurisdictionDto;
import com.civix.services.dto.ServiceDetailDto;
import com.civix.services.dto.ServiceSummaryDto;
import com.civix.services.service.DiscoveryService;
import com.civix.services.service.ServiceCatalogService;

@RestController
@RequestMapping("/api/v1/services")
public class ServiceController {

    private final ServiceCatalogService catalog;
    private final DiscoveryService discovery;
    private final EligibilityService eligibility;

    public ServiceController(ServiceCatalogService catalog, DiscoveryService discovery, EligibilityService eligibility) {
        this.catalog = catalog;
        this.discovery = discovery;
        this.eligibility = eligibility;
    }

    @GetMapping
    public PageResponse<ServiceSummaryDto> list(@RequestParam(required = false) String q,
                                                @RequestParam(required = false) ServiceCategory category,
                                                @RequestParam(required = false) JurisdictionLevel jurisdiction,
                                                @RequestParam(required = false) String state,
                                                @RequestParam(required = false) Integer page,
                                                @RequestParam(required = false) Integer size) {
        return catalog.list(q, category, jurisdiction, state, page, size, CurrentUser.idIfAuthenticated().orElse(null));
    }

    @GetMapping("/categories")
    public List<CategoryCountDto> categories() {
        return catalog.categories();
    }

    @GetMapping("/jurisdictions")
    public List<JurisdictionDto> jurisdictions() {
        return catalog.jurisdictions();
    }

    @GetMapping("/{id}")
    public ServiceDetailDto detail(@PathVariable UUID id) {
        return catalog.detail(id, CurrentUser.idIfAuthenticated().orElse(null));
    }

    @PostMapping("/discover")
    public DiscoveryResponseDto discover(@Valid @RequestBody DiscoverRequest request) {
        return discovery.discover(CurrentUser.id(), request);
    }

    @PostMapping("/{id}/eligibility")
    public EligibilityResultDto evaluate(@PathVariable UUID id) {
        return eligibility.evaluate(CurrentUser.id(), id);
    }
}
