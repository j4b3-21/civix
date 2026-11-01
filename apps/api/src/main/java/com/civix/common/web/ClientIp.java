package com.civix.common.web;

import java.util.Optional;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Client address helpers. With {@code server.forward-headers-strategy=framework} the remote address already
 * reflects {@code X-Forwarded-For} from the trusted proxy (Next.js / nginx).
 */
public final class ClientIp {

    private ClientIp() {
    }

    public static String of(HttpServletRequest request) {
        String addr = request.getRemoteAddr();
        return addr == null ? "unknown" : addr;
    }

    public static Optional<HttpServletRequest> currentRequest() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes servlet) {
            return Optional.of(servlet.getRequest());
        }
        return Optional.empty();
    }

    public static Optional<String> current() {
        return currentRequest().map(ClientIp::of);
    }
}
