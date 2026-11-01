package com.civix.agent.client;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.civix.citizen.dto.CitizenContext;
import com.civix.eligibility.dto.EligibilityResultDto;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Wire records of the Spring → agent internal API (docs/AGENT_CONTRACTS.md §3). The agent's Pydantic models
 * forbid unknown fields, so requests contain exactly the contract members (optional ones omitted when null).
 */
public final class AgentWire {

    private AgentWire() {
    }

    // ---- registry ----------------------------------------------------------------------------------

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RegistryServicesResponse(List<Map<String, Object>> services, String profileFieldsVersion,
                                           String generatedAt) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ProfileFieldsResponse(String version, List<Map<String, Object>> fields) {
    }

    // ---- eligibility & discovery -------------------------------------------------------------------

    public record EligibilityRequest(String serviceCode, CitizenContext citizenContext) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record DiscoveryRequest(String query, DiscoveryFilters filters, CitizenContext citizenContext,
                                   Boolean includeTest, Integer limit) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record DiscoveryFilters(String state, String category, String jurisdiction) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DiscoveryResponse(DiscoveryInterpretation interpretation, List<DiscoveryResult> results,
                                    String generatedAt) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DiscoveryInterpretation(String summary, List<String> categories, List<String> keywords, String state,
                                          Boolean usedLlm) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DiscoveryResult(String serviceCode, Double score, List<String> matchReasons,
                                  EligibilityResultDto eligibility, List<Map<String, Object>> sources) {
    }

    // ---- documents ---------------------------------------------------------------------------------

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ExtractRequest(UUID documentId, String storageKey, String mimeType, String declaredCategory,
                                 CitizenContext citizenContext, List<OtherDocument> otherDocuments) {
    }

    public record OtherDocument(UUID documentId, String category, List<Map<String, Object>> fields) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ExtractResponse(UUID documentId, Classification classification, List<Map<String, Object>> fields,
                                  List<Map<String, Object>> discrepancies, List<String> warnings, String engine,
                                  Integer textCharacters, Boolean requiresReview) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Classification(String category, Double confidence, Boolean declaredCategoryMatches) {
    }

    // ---- tasks -------------------------------------------------------------------------------------

    public record CreateTaskRequest(UUID taskId, UUID applicationId, UUID userId, String serviceCode, Intent intent,
                                    CitizenContext citizenContext, List<TaskDocument> documents, int dataVersion) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Intent(String type, String text) {
    }

    public record TaskDocument(UUID documentId, String category, String storageKey, String mimeType, String sha256,
                               String fileName, boolean reviewed) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ResumeRequest(String kind, ApprovalResume approval, CitizenInputResume citizenInput) {

        public static ResumeRequest approval(ApprovalResume approval) {
            return new ResumeRequest("APPROVAL", approval, null);
        }

        public static ResumeRequest citizenInput(CitizenInputResume input) {
            return new ResumeRequest("CITIZEN_INPUT", null, input);
        }

        public static ResumeRequest retry() {
            return new ResumeRequest("RETRY", null, null);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ApprovalResume(UUID approvalId, String approvalKind, String decision, String token,
                                 String payloadDigest, Instant expiresAt) {
        @Override
        public String toString() {
            return "ApprovalResume[approvalId=" + approvalId + ", decision=" + decision + ", token=" + (token == null ? "none" : "***") + "]";
        }
    }

    /** Carries an OTP/CAPTCHA/etc. Never logged: {@link #toString()} masks the value. */
    public record CitizenInputResume(String requestId, String value) {
        @Override
        public String toString() {
            return "CitizenInputResume[requestId=" + requestId + ", value=***]";
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CancelRequest(String reason) {
    }
}
