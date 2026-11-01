package com.civix.agent.client;

import org.springframework.http.HttpStatus;

import com.civix.common.api.ApiException;

/** The agent could not be reached or answered with an unusable response. */
public class AgentUnavailableException extends ApiException {

    private static final long serialVersionUID = 1L;

    public AgentUnavailableException(String detail, Throwable cause) {
        super("AGENT_UNAVAILABLE", HttpStatus.SERVICE_UNAVAILABLE, detail);
        if (cause != null) {
            initCause(cause);
        }
    }
}
