package com.civix.common.health;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

import com.civix.agent.client.AgentClient;

/** Reports agent reachability as a non-fatal detail (the API can serve most requests without the agent). */
@Component("agentHealthIndicator")
public class AgentHealthIndicator implements HealthIndicator {

    private final AgentClient agent;

    public AgentHealthIndicator(AgentClient agent) {
        this.agent = agent;
    }

    @Override
    public Health health() {
        return Health.up().withDetail("reachable", agent.isReachable()).build();
    }
}
