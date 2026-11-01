package com.civix.approvals.service;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import com.civix.common.crypto.Hashing;

/**
 * Approval tokens (docs/AGENT_CONTRACTS.md §4):
 * {@code base64url(payloadJson) + "." + base64url(HMAC_SHA256(secret, base64url(payloadJson)))}, base64url without
 * padding, payload = compact JSON with keys in the exact order {@code aid, tid, app, uid, kind, dig, exp}.
 * The agent and the browser worker verify it before any consequential action.
 */
public final class ApprovalTokenSigner {

    private final byte[] secret;

    public ApprovalTokenSigner(String secret) {
        if (secret == null || secret.length() < 16 || secret.startsWith("CHANGE_ME")) {
            throw new IllegalStateException("APPROVAL_SIGNING_SECRET must be set (at least 16 characters)");
        }
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    public String sign(UUID approvalId, UUID taskId, UUID applicationId, UUID userId, String kind, String payloadDigest,
                       long expiresAtEpochSeconds) {
        String payload = payloadJson(approvalId.toString(), taskId.toString(), applicationId.toString(),
                userId.toString(), kind, payloadDigest, expiresAtEpochSeconds);
        String encodedPayload = Hashing.base64UrlNoPadding(payload.getBytes(StandardCharsets.UTF_8));
        byte[] mac = Hashing.hmacSha256(secret, encodedPayload.getBytes(StandardCharsets.US_ASCII));
        return encodedPayload + "." + Hashing.base64UrlNoPadding(mac);
    }

    static String payloadJson(String aid, String tid, String app, String uid, String kind, String dig, long exp) {
        return "{\"aid\":" + quote(aid)
                + ",\"tid\":" + quote(tid)
                + ",\"app\":" + quote(app)
                + ",\"uid\":" + quote(uid)
                + ",\"kind\":" + quote(kind)
                + ",\"dig\":" + quote(dig)
                + ",\"exp\":" + exp + "}";
    }

    /** Minimal JSON string encoding (the values are UUIDs, enum names and hex digests in practice). */
    private static String quote(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 2).append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.append('"').toString();
    }
}
