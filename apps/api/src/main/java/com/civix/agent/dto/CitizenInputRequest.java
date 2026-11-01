package com.civix.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** OTP / CAPTCHA / declaration value. Forwarded to the agent, never persisted or logged. */
public record CitizenInputRequest(@NotBlank @Size(max = 64) String requestId, @NotBlank @Size(min = 1, max = 200) String value) {

    @Override
    public String toString() {
        return "CitizenInputRequest[requestId=" + requestId + ", value=***]";
    }
}
