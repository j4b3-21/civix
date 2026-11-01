package com.civix.approvals;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.civix.approvals.service.ApprovalTokenSigner;

/** The vector is shared with apps/agent/tests/test_policies.py (docs/AGENT_CONTRACTS.md §4). */
class ApprovalTokenSignerTest {

    static final String VECTOR = "eyJhaWQiOiIxMTExMTExMS0xMTExLTExMTEtMTExMS0xMTExMTExMTExMTEiLCJ0aWQiOiIyMjIyMjIyMi0yMjIyLTIyMjItMjIyMi0yMjIyMjIyMjIyMjIiLCJhcHAiOiIzMzMzMzMzMy0zMzMzLTMzMzMtMzMzMy0zMzMzMzMzMzMzMzMiLCJ1aWQiOiI0NDQ0NDQ0NC00NDQ0LTQ0NDQtNDQ0NC00NDQ0NDQ0NDQ0NDQiLCJraW5kIjoiRklOQUxfU1VCTUlTU0lPTiIsImRpZyI6ImFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWEiLCJleHAiOjE4OTM0NTYwMDB9"
            + ".akemDwop-UVWo6rtxQVZn0e9pDl8DaFOP1mv8utCLsE";

    @Test
    void matchesCrossLanguageVector() {
        String token = new ApprovalTokenSigner("test-approval-secret-0123456789abcdef").sign(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                UUID.fromString("33333333-3333-3333-3333-333333333333"),
                UUID.fromString("44444444-4444-4444-4444-444444444444"),
                "FINAL_SUBMISSION", "a".repeat(64), 1893456000L);
        assertThat(token).isEqualTo(VECTOR);
    }

    @Test
    void rejectsMissingOrPlaceholderSecret() {
        assertThatThrownBy(() -> new ApprovalTokenSigner(null)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new ApprovalTokenSigner("CHANGE_ME_please_1234")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new ApprovalTokenSigner("short")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void differentDigestGivesDifferentToken() {
        ApprovalTokenSigner signer = new ApprovalTokenSigner("test-approval-secret-0123456789abcdef");
        UUID id = UUID.randomUUID();
        assertThat(signer.sign(id, id, id, id, "PREPARATION", "a".repeat(64), 1L))
                .isNotEqualTo(signer.sign(id, id, id, id, "PREPARATION", "b".repeat(64), 1L));
    }
}
