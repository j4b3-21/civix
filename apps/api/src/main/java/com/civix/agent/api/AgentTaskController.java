package com.civix.agent.api;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.civix.agent.dto.AgentTaskDto;
import com.civix.agent.dto.CancelTaskRequest;
import com.civix.agent.dto.CitizenInputRequest;
import com.civix.agent.dto.ExecutionEventDto;
import com.civix.agent.service.AgentTaskService;
import com.civix.common.api.PageResponse;
import com.civix.common.api.Pagination;
import com.civix.common.security.CurrentUser;
import com.civix.common.web.Images;

@RestController
@RequestMapping("/api/v1/agent/tasks")
public class AgentTaskController {

    private final AgentTaskService tasks;

    public AgentTaskController(AgentTaskService tasks) {
        this.tasks = tasks;
    }

    @GetMapping
    public PageResponse<AgentTaskDto> list(@RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return PageResponse.from(tasks.list(CurrentUser.id(), Pagination.newestFirst(page, size, "createdAt")), x -> x);
    }

    @GetMapping("/{id}")
    public AgentTaskDto get(@PathVariable UUID id) {
        return tasks.get(CurrentUser.id(), id);
    }

    /** Live execution events (SSE). Reconnect with Last-Event-ID to replay everything after it. */
    @GetMapping(value = "/{id}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(@PathVariable UUID id,
                             @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId,
                             @RequestParam(value = "lastEventId", required = false) Long lastEventIdParam) {
        long after = parse(lastEventId, lastEventIdParam == null ? 0 : lastEventIdParam);
        return tasks.subscribe(CurrentUser.id(), id, after);
    }

    @GetMapping("/{id}/events/history")
    public List<ExecutionEventDto> history(@PathVariable UUID id,
                                           @RequestParam(defaultValue = "0") long afterSequence,
                                           @RequestParam(defaultValue = "200") int limit) {
        tasks.owned(CurrentUser.id(), id);
        return tasks.history(id, afterSequence, limit);
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<AgentTaskDto> cancel(@PathVariable UUID id, @Valid @RequestBody(required = false) CancelTaskRequest request) {
        String reason = request == null ? null : request.reason();
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(tasks.cancel(CurrentUser.id(), id, reason));
    }

    @PostMapping("/{id}/citizen-input")
    public ResponseEntity<Void> citizenInput(@PathVariable UUID id, @Valid @RequestBody CitizenInputRequest request) {
        tasks.citizenInput(CurrentUser.id(), id, request);
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/{id}/retry")
    public ResponseEntity<AgentTaskDto> retry(@PathVariable UUID id) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(tasks.retry(CurrentUser.id(), id));
    }

    @GetMapping("/{id}/screenshots/{screenshotId}")
    public ResponseEntity<InputStreamResource> screenshot(@PathVariable UUID id, @PathVariable UUID screenshotId) {
        return Images.png(tasks.screenshot(CurrentUser.id(), id, screenshotId));
    }

    private static long parse(String header, long fallback) {
        if (header == null || header.isBlank()) {
            return Math.max(0, fallback);
        }
        try {
            return Math.max(0, Long.parseLong(header.trim()));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
