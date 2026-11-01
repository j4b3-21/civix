package com.civix.privacy.api;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.civix.auth.service.AuthCookies;
import com.civix.common.security.CurrentUser;
import com.civix.privacy.dto.DeletionRequest;
import com.civix.privacy.dto.DeletionResultDto;
import com.civix.privacy.service.PrivacyService;

@RestController
@RequestMapping("/api/v1/privacy")
public class PrivacyController {

    private final PrivacyService privacy;
    private final AuthCookies cookies;

    public PrivacyController(PrivacyService privacy, AuthCookies cookies) {
        this.privacy = privacy;
        this.cookies = cookies;
    }

    @PostMapping("/deletion")
    public ResponseEntity<DeletionResultDto> delete(@Valid @RequestBody DeletionRequest request) {
        DeletionResultDto result = privacy.delete(CurrentUser.id(), request.scope());
        ResponseEntity.BodyBuilder ok = ResponseEntity.ok();
        if (result.accountDeleted()) {
            ok.headers(cookies.clear());
        }
        return ok.body(result);
    }
}
