package com.civix.notifications.api;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.civix.common.api.PageResponse;
import com.civix.common.api.Pagination;
import com.civix.common.security.CurrentUser;
import com.civix.notifications.dto.NotificationDto;
import com.civix.notifications.service.NotificationService;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notifications;

    public NotificationController(NotificationService notifications) {
        this.notifications = notifications;
    }

    @GetMapping
    public PageResponse<NotificationDto> list(@RequestParam(defaultValue = "false") boolean unreadOnly,
                                              @RequestParam(required = false) Integer page,
                                              @RequestParam(required = false) Integer size) {
        return PageResponse.from(notifications.list(CurrentUser.id(), unreadOnly,
                Pagination.newestFirst(page, size, "createdAt")), x -> x);
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<Void> markRead(@PathVariable UUID id) {
        notifications.markRead(CurrentUser.id(), id);
        return ResponseEntity.noContent().build();
    }
}
