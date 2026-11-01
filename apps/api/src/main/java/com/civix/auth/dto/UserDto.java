package com.civix.auth.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.civix.auth.domain.Role;
import com.civix.auth.domain.User;

public record UserDto(UUID id, String email, String fullName, List<Role> roles, Instant createdAt) {

    public static UserDto from(User user) {
        return new UserDto(user.getId(), user.getEmail(), user.getFullName(), user.roles(), user.getCreatedAt());
    }
}
