package com.civix.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Email @Size(max = 320) String email,
        @NotNull @Size(min = 12, max = 128) String password,
        @NotBlank @Size(min = 1, max = 120) String fullName) {

    @Override
    public String toString() {
        return "RegisterRequest[email=" + email + ", password=***, fullName=" + fullName + "]";
    }
}
