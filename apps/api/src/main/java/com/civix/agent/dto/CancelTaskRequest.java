package com.civix.agent.dto;

import jakarta.validation.constraints.Size;

public record CancelTaskRequest(@Size(max = 300) String reason) {
}
