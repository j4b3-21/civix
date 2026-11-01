package com.civix.common.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import com.civix.common.api.Problems;

import tools.jackson.databind.ObjectMapper;

/** Writes problem+json from servlet filters / security handlers, where MVC message conversion is not available. */
public class SecurityProblemWriter {

    private final ObjectMapper mapper;

    public SecurityProblemWriter(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public void write(HttpServletResponse response, HttpStatus status, String code, String detail) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        Map<String, Object> body = Problems.asMap(status, code, detail);
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(mapper.writeValueAsString(body));
        response.getWriter().flush();
    }
}
