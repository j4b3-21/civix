package com.civix.agent.client;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;

import com.civix.common.api.ApiException;

/** The agent answered with an HTTP error status (4xx/5xx). */
public class AgentRejectedException extends ApiException {

    private static final long serialVersionUID = 1L;

    private final int agentStatus;

    public AgentRejectedException(int agentStatus) {
        super("AGENT_ERROR", HttpStatus.BAD_GATEWAY, "The automation service rejected the request (HTTP " + agentStatus + ")",
                List.of(), Map.of("agentStatus", agentStatus));
        this.agentStatus = agentStatus;
    }

    public int agentStatus() {
        return agentStatus;
    }
}
