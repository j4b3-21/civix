package com.civix.agent.service;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.civix.agent.dto.ExecutionEventDto;
import com.civix.agent.repository.ExecutionEventRepository;
import com.civix.config.CivixProperties;

/**
 * In-memory fan-out of execution events to SSE subscribers, keyed by task id. Each subscriber replays the backlog
 * from {@code execution_events} first; live events arriving meanwhile are buffered and de-duplicated by sequence,
 * so a client always sees strictly increasing ids. A periodic catch-up from the database delivers events that
 * were ingested by another API instance.
 */
@Component
public class SseHub {

    private static final Logger log = LoggerFactory.getLogger(SseHub.class);
    public static final Duration EMITTER_TIMEOUT = Duration.ofMinutes(30);
    private static final int PAGE = 500;

    private final ConcurrentHashMap<UUID, CopyOnWriteArrayList<Subscriber>> subscribers = new ConcurrentHashMap<>();
    private final ExecutionEventRepository events;
    private final boolean pollFallback;

    public SseHub(ExecutionEventRepository events, CivixProperties props) {
        this.events = events;
        this.pollFallback = props.sse() == null || props.sse().pollFallback();
    }

    /** Registers a subscriber, replays everything after {@code lastEventId} and then streams live events. */
    public SseEmitter subscribe(UUID taskId, long lastEventId) {
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT.toMillis());
        Subscriber sub = new Subscriber(taskId, emitter, lastEventId);
        subscribers.computeIfAbsent(taskId, k -> new CopyOnWriteArrayList<>()).add(sub);
        emitter.onCompletion(() -> remove(sub));
        emitter.onTimeout(() -> {
            remove(sub);
            emitter.complete();
        });
        emitter.onError(t -> remove(sub));
        try {
            sub.finishReplay(load(taskId, lastEventId));
        } catch (RuntimeException e) {
            log.warn("SSE replay for task {} failed: {}", taskId, e.getClass().getSimpleName());
            remove(sub);
            emitter.completeWithError(e);
        }
        return emitter;
    }

    /** Publishes after the surrounding transaction commits (immediately if there is none). */
    public void publishAfterCommit(ExecutionEventDto event) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publish(event);
                }
            });
        } else {
            publish(event);
        }
    }

    public void publish(ExecutionEventDto event) {
        List<Subscriber> subs = subscribers.get(event.taskId());
        if (subs == null) {
            return;
        }
        for (Subscriber sub : subs) {
            sub.offerLive(event);
        }
    }

    public int subscriberCount() {
        return subscribers.values().stream().mapToInt(List::size).sum();
    }

    @Scheduled(fixedRate = 15_000L, initialDelay = 15_000L)
    public void heartbeat() {
        for (List<Subscriber> subs : subscribers.values()) {
            for (Subscriber sub : subs) {
                sub.ping();
            }
        }
    }

    /** Cross-instance delivery: pulls events newer than what each subscriber has seen. */
    @Scheduled(fixedDelay = 5_000L, initialDelay = 5_000L)
    public void catchUp() {
        if (!pollFallback) {
            return;
        }
        for (Map.Entry<UUID, CopyOnWriteArrayList<Subscriber>> entry : subscribers.entrySet()) {
            List<Subscriber> subs = entry.getValue();
            if (subs.isEmpty()) {
                continue;
            }
            long min = subs.stream().mapToLong(Subscriber::lastSent).min().orElse(0);
            try {
                List<ExecutionEventDto> backlog = load(entry.getKey(), min);
                for (ExecutionEventDto e : backlog) {
                    for (Subscriber sub : subs) {
                        sub.offerLive(e);
                    }
                }
            } catch (RuntimeException e) {
                log.debug("SSE catch-up for task {} failed: {}", entry.getKey(), e.getClass().getSimpleName());
            }
        }
    }

    private List<ExecutionEventDto> load(UUID taskId, long after) {
        List<ExecutionEventDto> all = new ArrayList<>();
        long cursor = after;
        while (true) {
            List<ExecutionEventDto> page = events
                    .findByTaskIdAndSequenceGreaterThanOrderBySequenceAsc(taskId, cursor, PageRequest.of(0, PAGE))
                    .stream().map(ExecutionEventDto::from).toList();
            all.addAll(page);
            if (page.size() < PAGE) {
                return all;
            }
            cursor = page.get(page.size() - 1).sequence();
        }
    }

    private void remove(Subscriber sub) {
        subscribers.computeIfPresent(sub.taskId, (k, list) -> {
            list.remove(sub);
            return list.isEmpty() ? null : list;
        });
    }

    /** One SSE connection. All sends are serialised on the subscriber's monitor. */
    final class Subscriber {

        private final UUID taskId;
        private final SseEmitter emitter;
        private long lastSent;
        private boolean replaying = true;
        private final List<ExecutionEventDto> pending = new ArrayList<>();
        private volatile boolean closed;

        Subscriber(UUID taskId, SseEmitter emitter, long lastEventId) {
            this.taskId = taskId;
            this.emitter = emitter;
            this.lastSent = Math.max(0, lastEventId);
        }

        synchronized long lastSent() {
            return lastSent;
        }

        synchronized void offerLive(ExecutionEventDto event) {
            if (closed) {
                return;
            }
            if (replaying) {
                pending.add(event);
            } else {
                sendIfNew(event);
            }
        }

        synchronized void finishReplay(List<ExecutionEventDto> backlog) {
            for (ExecutionEventDto e : backlog) {
                sendIfNew(e);
            }
            pending.sort(Comparator.comparingLong(ExecutionEventDto::sequence));
            for (ExecutionEventDto e : pending) {
                sendIfNew(e);
            }
            pending.clear();
            replaying = false;
        }

        synchronized void ping() {
            if (closed) {
                return;
            }
            try {
                emitter.send(SseEmitter.event().comment("ping"));
            } catch (IOException | IllegalStateException e) {
                close();
            }
        }

        private void sendIfNew(ExecutionEventDto e) {
            if (closed || e.sequence() <= lastSent) {
                return;
            }
            try {
                emitter.send(SseEmitter.event()
                        .id(Long.toString(e.sequence()))
                        .name(e.type())
                        .data(e, MediaType.APPLICATION_JSON));
                lastSent = e.sequence();
            } catch (IOException | IllegalStateException ex) {
                close();
            }
        }

        private void close() {
            closed = true;
            remove(this);
            try {
                emitter.complete();
            } catch (RuntimeException ignored) {
                // already completed
            }
        }
    }
}
