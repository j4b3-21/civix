package com.civix.agent.client;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.civix.agent.client.AgentWire.CancelRequest;
import com.civix.agent.client.AgentWire.CreateTaskRequest;
import com.civix.agent.client.AgentWire.DiscoveryRequest;
import com.civix.agent.client.AgentWire.DiscoveryResponse;
import com.civix.agent.client.AgentWire.EligibilityRequest;
import com.civix.agent.client.AgentWire.ExtractRequest;
import com.civix.agent.client.AgentWire.ExtractResponse;
import com.civix.agent.client.AgentWire.ProfileFieldsResponse;
import com.civix.agent.client.AgentWire.RegistryServicesResponse;
import com.civix.agent.client.AgentWire.ResumeRequest;
import com.civix.config.CivixProperties;
import com.civix.eligibility.dto.EligibilityResultDto;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Client of the agent's internal API ({@code AGENT_INTERNAL_URL}, {@code Authorization: Bearer AGENT_INTERNAL_TOKEN}).
 * Bodies are (de)serialised with the application's ObjectMapper and exchanged as bytes, so the wire format does
 * not depend on RestClient converter configuration. Request/response bodies are never logged.
 */
@Component
public class AgentClient {

    private static final Logger log = LoggerFactory.getLogger(AgentClient.class);

    private final RestClient fast;
    private final RestClient slow;
    private final RestClient health;
    private final ObjectMapper mapper;

    public AgentClient(CivixProperties props, ObjectMapper mapper) {
        CivixProperties.Agent cfg = props.agent();
        this.mapper = mapper;
        this.fast = build(cfg, cfg.readTimeoutMs(), true);
        this.slow = build(cfg, cfg.longReadTimeoutMs(), true);
        this.health = build(cfg, 3000, false);
    }

    private static RestClient build(CivixProperties.Agent cfg, int readTimeoutMs, boolean authenticated) {
        HttpClient http = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofMillis(cfg.connectTimeoutMs()))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
        RestClient.Builder builder = RestClient.builder().baseUrl(cfg.baseUrl()).requestFactory(factory);
        if (authenticated) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + cfg.token());
        }
        return builder.build();
    }

    // ---- registry ----------------------------------------------------------------------------------

    public RegistryServicesResponse fetchServices(boolean includeTest) {
        return call(slow, HttpMethod.GET, "/internal/v1/registry/services?includeTest=" + includeTest, null,
                RegistryServicesResponse.class);
    }

    public ProfileFieldsResponse fetchProfileFields() {
        return call(slow, HttpMethod.GET, "/internal/v1/registry/profile-fields", null, ProfileFieldsResponse.class);
    }

    // ---- eligibility / discovery / documents ---------------------------------------------------------

    public EligibilityResultDto evaluateEligibility(EligibilityRequest request) {
        return call(slow, HttpMethod.POST, "/internal/v1/eligibility/evaluate", request, EligibilityResultDto.class);
    }

    public DiscoveryResponse discover(DiscoveryRequest request) {
        return call(slow, HttpMethod.POST, "/internal/v1/discovery", request, DiscoveryResponse.class);
    }

    public ExtractResponse extractDocument(ExtractRequest request) {
        return call(slow, HttpMethod.POST, "/internal/v1/documents/extract", request, ExtractResponse.class);
    }

    // ---- tasks -------------------------------------------------------------------------------------

    public void createTask(CreateTaskRequest request) {
        call(fast, HttpMethod.POST, "/internal/v1/tasks", request, Void.class);
    }

    public void resume(UUID taskId, ResumeRequest request) {
        call(fast, HttpMethod.POST, "/internal/v1/tasks/" + taskId + "/resume", request, Void.class);
    }

    public void cancel(UUID taskId, String reason) {
        call(fast, HttpMethod.POST, "/internal/v1/tasks/" + taskId + "/cancel", new CancelRequest(reason), Void.class);
    }

    /** Reachability for the readiness detail; never throws. */
    public boolean isReachable() {
        try {
            health.get().uri("/internal/v1/health").retrieve().toBodilessEntity();
            return true;
        } catch (RestClientException e) {
            return false;
        }
    }

    private <T> T call(RestClient client, HttpMethod method, String path, Object body, Class<T> responseType) {
        try {
            RestClient.RequestBodySpec spec = client.method(method).uri(path).accept(MediaType.APPLICATION_JSON);
            if (body != null) {
                spec.contentType(MediaType.APPLICATION_JSON).body(mapper.writeValueAsBytes(body));
            }
            byte[] response = spec.retrieve().body(byte[].class);
            if (responseType == Void.class || response == null || response.length == 0) {
                return null;
            }
            return mapper.readValue(response, responseType);
        } catch (RestClientResponseException e) {
            log.warn("Agent call {} {} failed with HTTP {}", method, pathOnly(path), e.getStatusCode().value());
            throw new AgentRejectedException(e.getStatusCode().value());
        } catch (RestClientException e) {
            log.warn("Agent call {} {} failed: {}", method, pathOnly(path), e.getClass().getSimpleName());
            throw new AgentUnavailableException("The automation service is not reachable right now. Please try again shortly.", e);
        } catch (JacksonException e) {
            log.warn("Agent call {} {} returned an unreadable response", method, pathOnly(path));
            throw new AgentUnavailableException("The automation service returned an invalid response.", e);
        }
    }

    private static String pathOnly(String path) {
        int q = path.indexOf('?');
        return q >= 0 ? path.substring(0, q) : path;
    }
}
