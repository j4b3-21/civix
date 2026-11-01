package com.civix.applications.service;

import java.time.Clock;
import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.civix.applications.domain.Application;
import com.civix.applications.domain.ApplicationStatus;
import com.civix.applications.domain.ApplicationStatusHistory;
import com.civix.applications.domain.TransitionPolicy;
import com.civix.applications.repository.ApplicationStatusHistoryRepository;
import com.civix.common.api.ApiException;

/** The only place application statuses change; every change is validated and recorded in history. */
@Service
public class ApplicationStateService {

    private final ApplicationStatusHistoryRepository history;
    private final Clock clock;

    public ApplicationStateService(ApplicationStatusHistoryRepository history, Clock clock) {
        this.history = history;
        this.clock = clock;
    }

    /** @return true if the status changed. Throws 409 ILLEGAL_TRANSITION for transitions outside docs/WORKFLOWS.md §3. */
    @Transactional
    public boolean transition(Application app, ApplicationStatus next, String reason) {
        ApplicationStatus from = app.getStatus();
        if (from == next) {
            return false;
        }
        if (!TransitionPolicy.canTransition(from, next)) {
            throw new ApiException("ILLEGAL_TRANSITION", HttpStatus.CONFLICT,
                    "The application cannot move from " + from + " to " + next + ".");
        }
        Instant now = clock.instant();
        history.save(new ApplicationStatusHistory(app.getId(), from, next, reason == null ? null : truncate(reason), now));
        app.changeStatus(next, now);
        return true;
    }

    private static String truncate(String s) {
        return s.length() <= 255 ? s : s.substring(0, 255);
    }
}
