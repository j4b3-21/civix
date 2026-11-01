package com.civix.applications;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.civix.agent.domain.TaskStatus;
import com.civix.applications.domain.ApplicationStatus;
import com.civix.applications.domain.TransitionPolicy;

class TransitionPolicyTest {

    @Test
    void taskHappyPath() {
        assertThat(TransitionPolicy.canTransition(TaskStatus.QUEUED, TaskStatus.RUNNING)).isTrue();
        assertThat(TransitionPolicy.canTransition(TaskStatus.RUNNING, TaskStatus.WAITING_APPROVAL)).isTrue();
        assertThat(TransitionPolicy.canTransition(TaskStatus.WAITING_APPROVAL, TaskStatus.RUNNING)).isTrue();
        assertThat(TransitionPolicy.canTransition(TaskStatus.RUNNING, TaskStatus.WAITING_CITIZEN)).isTrue();
        assertThat(TransitionPolicy.canTransition(TaskStatus.RUNNING, TaskStatus.COMPLETED)).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = TaskStatus.class, names = {"QUEUED", "RUNNING", "WAITING_APPROVAL", "WAITING_CITIZEN", "FAILED"})
    void completedAndCancelledAreTerminal(TaskStatus next) {
        assertThat(TransitionPolicy.canTransition(TaskStatus.COMPLETED, next)).isFalse();
        assertThat(TransitionPolicy.canTransition(TaskStatus.CANCELLED, next)).isFalse();
    }

    @Test
    void retryOnlyWhenRetryable() {
        assertThat(TransitionPolicy.canTransition(TaskStatus.FAILED, TaskStatus.QUEUED, true)).isTrue();
        assertThat(TransitionPolicy.canTransition(TaskStatus.FAILED, TaskStatus.QUEUED, false)).isFalse();
        assertThat(TransitionPolicy.canTransition(TaskStatus.FAILED, TaskStatus.RUNNING)).isFalse();
    }

    @Test
    void sameStateIsANoOp() {
        for (TaskStatus s : TaskStatus.values()) {
            assertThat(TransitionPolicy.canTransition(s, s)).isTrue();
        }
        for (ApplicationStatus s : ApplicationStatus.values()) {
            assertThat(TransitionPolicy.canTransition(s, s)).isTrue();
        }
    }

    @Test
    void applicationRules() {
        assertThat(TransitionPolicy.canTransition(ApplicationStatus.DRAFT, ApplicationStatus.IN_PROGRESS)).isTrue();
        assertThat(TransitionPolicy.canTransition(ApplicationStatus.IN_PROGRESS, ApplicationStatus.SUBMISSION_UNCERTAIN)).isTrue();
        assertThat(TransitionPolicy.canTransition(ApplicationStatus.SUBMISSION_UNCERTAIN, ApplicationStatus.SUBMITTED)).isTrue();
        // An uncertain submission must never be re-run automatically.
        assertThat(TransitionPolicy.canTransition(ApplicationStatus.SUBMISSION_UNCERTAIN, ApplicationStatus.IN_PROGRESS)).isFalse();
        assertThat(TransitionPolicy.canTransition(ApplicationStatus.SUBMITTED, ApplicationStatus.IN_PROGRESS)).isFalse();
        assertThat(TransitionPolicy.canTransition(ApplicationStatus.CANCELLED, ApplicationStatus.DRAFT)).isFalse();
        assertThat(TransitionPolicy.allowedFrom(ApplicationStatus.SUBMITTED)).isEmpty();
        assertThat(TransitionPolicy.allowedFrom(TaskStatus.COMPLETED)).isEmpty();
    }
}
