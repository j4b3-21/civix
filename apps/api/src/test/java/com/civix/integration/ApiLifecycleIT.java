package com.civix.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import tools.jackson.databind.ObjectMapper;

import com.civix.agent.client.AgentClient;
import com.civix.agent.client.AgentWire.CreateTaskRequest;
import com.civix.agent.client.AgentWire.ResumeRequest;

/**
 * End-to-end API lifecycle against a real PostgreSQL (Flyway + JPA validate), with the agent replaced by a mock:
 * register → consent → application → execute → agent events (incl. an illegal transition) → approval → decision →
 * SSE history ordering; plus authorization: other users get 404, internal endpoints need the service token.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApiLifecycleIT {

    static final String INTERNAL_TOKEN = "it-internal-token-0123456789";

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("civix.jwt.secret", () -> "it-jwt-secret-it-jwt-secret-it-jwt-secret-0123");
        r.add("civix.audit.ip-pepper", () -> "it-pepper");
        r.add("civix.internal.api-token", () -> INTERNAL_TOKEN);
        r.add("civix.approval.signing-secret", () -> "test-approval-secret-0123456789abcdef");
        r.add("civix.agent.token", () -> "it-agent-token");
        r.add("civix.cookie.secure", () -> "false");
        r.add("civix.enable-test-services", () -> "true");
        r.add("civix.registry.sync-on-startup", () -> "false");
        r.add("civix.rate-limit.enabled", () -> "false");
        r.add("civix.storage.init-bucket", () -> "false");
        r.add("civix.storage.access-key", () -> "it");
        r.add("civix.storage.secret-key", () -> "it");
        r.add("civix.storage.endpoint", () -> "http://127.0.0.1:1");
    }

    @MockitoBean
    AgentClient agent;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ObjectMapper mapper;

    @Value("${local.server.port}")
    int port;

    // ------------------------------------------------------------------ helpers

    final class Client {
        final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        final HttpClient http = HttpClient.newBuilder().cookieHandler(cookies).build();

        HttpResponse<String> send(String method, String path, Object body, Map<String, String> headers) throws Exception {
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                    .header("Content-Type", "application/json").header("Accept", "application/json");
            String xsrf = cookies.getCookieStore().getCookies().stream().filter(c -> c.getName().equals("XSRF-TOKEN"))
                    .map(HttpCookie::getValue).findFirst().orElse(null);
            if (xsrf != null) {
                b.header("X-XSRF-TOKEN", xsrf);
            }
            headers.forEach(b::header);
            b.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)));
            return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
        }

        HttpResponse<String> get(String path) throws Exception {
            return send("GET", path, null, Map.of());
        }

        HttpResponse<String> post(String path, Object body) throws Exception {
            return send("POST", path, body, Map.of());
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> json(HttpResponse<String> r) {
            return mapper.readValue(r.body(), Map.class);
        }
    }

    Client registered(String email) throws Exception {
        Client c = new Client();
        HttpResponse<String> r = c.post("/api/v1/auth/register",
                Map.of("email", email, "password", "correct horse battery staple", "fullName", "IT Citizen"));
        assertThat(r.statusCode()).as(r.body()).isEqualTo(201);
        assertThat(c.get("/api/v1/auth/me").statusCode()).isEqualTo(200); // also issues the XSRF-TOKEN cookie
        return c;
    }

    HttpResponse<String> internal(String path, Object body, String token) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json");
        if (token != null) {
            b.header("Authorization", "Bearer " + token);
        }
        b.POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)));
        return HttpClient.newHttpClient().send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    UUID insertTestService() {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into civix.service_definitions (id, code, version, name, summary, description, department, category,
                  jurisdiction_level, official_url, verification_status, automation_status, is_test_service, definition, content_hash, active)
                values (?, 'IN_TEST_PORTAL_SCHOLARSHIP', 1, 'CIVIX Test Portal — Demo', 'Test-only scholarship for integration tests.',
                  'Test environment.', 'CIVIX engineering', 'SCHOLARSHIPS', 'CENTRAL', 'http://test-portal:8090/', 'WORKFLOW_VERIFIED',
                  'TEST_ONLY', true, '{}'::jsonb, repeat('a', 64), true)
                on conflict do nothing
                """, id);
        return jdbc.queryForObject("select id from civix.service_definitions where code = 'IN_TEST_PORTAL_SCHOLARSHIP' and active", UUID.class);
    }

    static Map<String, Object> event(UUID taskId, String type, String taskStatus, String appStatus, Map<String, Object> metadata) {
        Map<String, Object> e = new java.util.LinkedHashMap<>();
        e.put("eventId", UUID.randomUUID().toString());
        e.put("taskId", taskId.toString());
        e.put("type", type);
        e.put("timestamp", Instant.now().toString());
        e.put("agent", "ORCHESTRATOR");
        e.put("step", null);
        e.put("message", type);
        e.put("metadata", metadata);
        if (taskStatus != null) {
            e.put("taskStatus", taskStatus);
        }
        if (appStatus != null) {
            e.put("applicationStatus", appStatus);
        }
        return e;
    }

    // ------------------------------------------------------------------ tests

    @Test
    @SuppressWarnings("unchecked")
    void fullLifecycleWithAuthorizationAndApproval() throws Exception {
        UUID serviceId = insertTestService();
        Client asha = registered("asha+" + UUID.randomUUID() + "@example.org");
        Client other = registered("other+" + UUID.randomUUID() + "@example.org");

        assertThat(asha.post("/api/v1/profile/consents", Map.of("purpose", "PROFILE_PROCESSING", "granted", true)).statusCode()).isEqualTo(200);
        assertThat(asha.post("/api/v1/profile/consents", Map.of("purpose", "AGENT_EXECUTION", "granted", true)).statusCode()).isEqualTo(200);

        HttpResponse<String> created = asha.post("/api/v1/applications", Map.of("serviceId", serviceId.toString()));
        assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
        String applicationId = (String) asha.json(created).get("id");
        assertThat(asha.post("/api/v1/applications", Map.of("serviceId", serviceId.toString())).statusCode()).isEqualTo(409);

        // ownership: another citizen cannot see it (404, not 403)
        assertThat(other.get("/api/v1/applications/" + applicationId).statusCode()).isEqualTo(404);

        HttpResponse<String> exec = asha.post("/api/v1/applications/" + applicationId + "/execute", null);
        assertThat(exec.statusCode()).as(exec.body()).isEqualTo(202);
        UUID taskId = UUID.fromString((String) asha.json(exec).get("id"));
        ArgumentCaptor<CreateTaskRequest> createReq = ArgumentCaptor.forClass(CreateTaskRequest.class);
        verify(agent).createTask(createReq.capture());
        assertThat(createReq.getValue().taskId()).isEqualTo(taskId);
        assertThat(createReq.getValue().citizenContext().consents()).contains("AGENT_EXECUTION");

        // internal endpoints require the service token
        assertThat(internal("/internal/v1/agent-events", Map.of("events", List.of()), null).statusCode()).isEqualTo(401);
        assertThat(internal("/internal/v1/agent-events", Map.of("events", List.of()), "wrong").statusCode()).isEqualTo(401);

        Map<String, Object> started = event(taskId, "TASK_STARTED", "RUNNING", "IN_PROGRESS", Map.of());
        HttpResponse<String> r = internal("/internal/v1/agent-events",
                Map.of("events", List.of(started, started, event(taskId, "TASK_CREATED", "QUEUED", null, Map.of()))), INTERNAL_TOKEN);
        assertThat(r.statusCode()).as(r.body()).isEqualTo(200);
        Map<String, Object> ingest = mapper.readValue(r.body(), Map.class);
        assertThat(ingest.get("accepted")).isEqualTo(2);
        assertThat(ingest.get("duplicates")).isEqualTo(1);
        assertThat(ingest.get("rejectedTransitions")).isEqualTo(1); // RUNNING -> QUEUED is not allowed

        Map<String, Object> task = asha.json(asha.get("/api/v1/agent/tasks/" + taskId));
        assertThat(task.get("status")).isEqualTo("RUNNING");
        assertThat(other.get("/api/v1/agent/tasks/" + taskId).statusCode()).isEqualTo(404);

        // the agent requests an approval
        HttpResponse<String> approval = internal("/internal/v1/approvals", Map.of(
                "taskId", taskId.toString(), "applicationId", applicationId, "kind", "PREPARATION", "actionClass", "REVERSIBLE",
                "summary", "Fill 3 fields", "details", Map.of("fields", List.of(Map.of("label", "Full name", "value", "Asha"))),
                "payloadDigest", "c".repeat(64), "dataVersion", 1, "expiresInSeconds", 600), INTERNAL_TOKEN);
        assertThat(approval.statusCode()).as(approval.body()).isEqualTo(201);
        String approvalId = (String) mapper.readValue(approval.body(), Map.class).get("approvalId");
        internal("/internal/v1/agent-events", Map.of("events", List.of(event(taskId, "APPROVAL_REQUIRED", "WAITING_APPROVAL",
                "ACTION_REQUIRED", Map.of("approvalId", approvalId, "kind", "PREPARATION")))), INTERNAL_TOKEN);

        Map<String, Object> app = asha.json(asha.get("/api/v1/applications/" + applicationId));
        assertThat(app.get("status")).isEqualTo("ACTION_REQUIRED");
        assertThat(((Map<String, Object>) app.get("requiredAction")).get("type")).isEqualTo("APPROVAL");

        // a stale data version is refused and invalidates the approval
        assertThat(other.post("/api/v1/approvals/" + approvalId + "/decision", Map.of("decision", "APPROVE", "dataVersion", 1)).statusCode())
                .isEqualTo(404);
        HttpResponse<String> decided = asha.post("/api/v1/approvals/" + approvalId + "/decision", Map.of("decision", "APPROVE", "dataVersion", 1));
        assertThat(decided.statusCode()).as(decided.body()).isEqualTo(200);
        assertThat(asha.json(decided).get("status")).isEqualTo("APPROVED");
        assertThat(decided.body()).doesNotContain("token");
        ArgumentCaptor<ResumeRequest> resume = ArgumentCaptor.forClass(ResumeRequest.class);
        verify(agent).resume(eq(taskId), resume.capture());
        assertThat(resume.getValue().approval().decision()).isEqualTo("APPROVE");
        assertThat(resume.getValue().approval().token()).contains(".");
        assertThat(asha.post("/api/v1/approvals/" + approvalId + "/decision", Map.of("decision", "APPROVE", "dataVersion", 1)).statusCode())
                .isEqualTo(409);

        // event history is strictly ordered by sequence
        List<Map<String, Object>> history = mapper.readValue(asha.get("/api/v1/agent/tasks/" + taskId + "/events/history").body(), List.class);
        assertThat(history).extracting(h -> h.get("type")).containsExactly("TASK_STARTED", "TASK_CREATED", "APPROVAL_REQUIRED");
        long a = ((Number) history.get(0).get("sequence")).longValue();
        long b = ((Number) history.get(2).get("sequence")).longValue();
        assertThat(b).isGreaterThan(a);

        // citizen input is refused when the agent is not waiting for it
        assertThat(asha.post("/api/v1/agent/tasks/" + taskId + "/citizen-input", Map.of("requestId", "x", "value", "123456")).statusCode())
                .isEqualTo(409);
        verify(agent, org.mockito.Mockito.times(1)).resume(eq(taskId), any(ResumeRequest.class)); // only the approval was forwarded
    }

    @Test
    void unauthenticatedAndCsrf() throws Exception {
        Client anon = new Client();
        assertThat(anon.get("/api/v1/applications").statusCode()).isEqualTo(401);
        Client c = registered("csrf+" + UUID.randomUUID() + "@example.org");
        HttpResponse<String> noCsrf = c.http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/profile/consents"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"purpose\":\"PROFILE_PROCESSING\",\"granted\":true}")).build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(noCsrf.statusCode()).isEqualTo(403);
    }
}
