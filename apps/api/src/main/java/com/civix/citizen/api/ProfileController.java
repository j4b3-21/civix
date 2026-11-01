package com.civix.citizen.api;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.civix.citizen.dto.ProfileDto;
import com.civix.citizen.dto.UpdateProfileRequest;
import com.civix.citizen.service.ProfileService;
import com.civix.common.security.CurrentUser;

@RestController
@RequestMapping("/api/v1/profile")
public class ProfileController {

    private final ProfileService profiles;

    public ProfileController(ProfileService profiles) {
        this.profiles = profiles;
    }

    @GetMapping
    public ProfileDto get() {
        return profiles.get(CurrentUser.id());
    }

    @PutMapping
    public ProfileDto update(@Valid @RequestBody UpdateProfileRequest request) {
        return profiles.update(CurrentUser.id(), request);
    }
}
