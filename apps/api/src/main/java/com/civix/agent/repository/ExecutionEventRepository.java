package com.civix.agent.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.civix.agent.domain.ExecutionEvent;

public interface ExecutionEventRepository extends JpaRepository<ExecutionEvent, Long> {

    boolean existsByEventId(UUID eventId);

    List<ExecutionEvent> findByTaskIdAndSequenceGreaterThanOrderBySequenceAsc(UUID taskId, long afterSequence,
                                                                             Pageable pageable);
}
