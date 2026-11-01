package com.civix.consent.api;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.civix.common.security.CurrentUser;
import com.civix.consent.dto.ConsentDto;
import com.civix.consent.dto.ConsentUpdateRequest;
import com.civix.consent.service.ConsentService;

@RestController
@RequestMapping("/api/v1/profile/consents")
public class ConsentController {

    private final ConsentService consents;

    public ConsentController(ConsentService consents) {
        this.consents = consents;
    }

    @GetMapping
    public List<ConsentDto> list() {
        return consents.current(CurrentUser.id());
    }

    @PostMapping
    public ConsentDto update(@Valid @RequestBody ConsentUpdateRequest request) {
        return consents.update(CurrentUser.id(), request.purpose(), request.granted());
    }
}
