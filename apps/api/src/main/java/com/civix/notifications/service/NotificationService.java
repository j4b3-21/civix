package com.civix.notifications.service;

import java.time.Clock;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.civix.common.api.ApiException;
import com.civix.notifications.domain.Notification;
import com.civix.notifications.dto.NotificationDto;
import com.civix.notifications.repository.NotificationRepository;

@Service
public class NotificationService {

    private final NotificationRepository notifications;
    private final Clock clock;

    public NotificationService(NotificationRepository notifications, Clock clock) {
        this.notifications = notifications;
        this.clock = clock;
    }

    @Transactional
    public void notify(UUID userId, String type, String title, String body, String link) {
        notifications.save(new Notification(userId, type, truncate(title, 200), truncate(body, 1000),
                truncate(link, 500), clock.instant()));
    }

    /** Creates the notification unless one of the same type already points at the same link. */
    @Transactional
    public void notifyOnce(UUID userId, String type, String title, String body, String link) {
        if (link != null && notifications.existsByUserIdAndTypeAndLink(userId, type, link)) {
            return;
        }
        notify(userId, type, title, body, link);
    }

    @Transactional(readOnly = true)
    public Page<NotificationDto> list(UUID userId, boolean unreadOnly, Pageable pageable) {
        Page<Notification> page = unreadOnly ? notifications.findByUserIdAndReadAtIsNull(userId, pageable)
                : notifications.findByUserId(userId, pageable);
        return page.map(NotificationDto::from);
    }

    @Transactional
    public void markRead(UUID userId, UUID notificationId) {
        Notification n = notifications.findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> ApiException.notFound("Notification"));
        n.markRead(clock.instant());
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
