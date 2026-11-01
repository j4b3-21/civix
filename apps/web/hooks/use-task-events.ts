"use client";

import { useCallback, useEffect, useReducer, useRef, useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { getTask, getTaskEventHistory, taskEventsStreamPath } from "@/lib/api/agent";
import { isApiError } from "@/lib/api/client";
import { queryKeys } from "@/lib/query-keys";
import {
  STATUS_CHANGING_EVENT_TYPES,
  TaskEventStream,
  browserEventSourceFactory,
  initialTaskEventsState,
  taskEventsReducer,
  type EventSourceFactory,
  type TaskEventsState,
} from "@/lib/sse/task-events";
import { TERMINAL_TASK_STATUSES } from "@/features/agent/queries";

export interface UseTaskEventsOptions {
  enabled?: boolean;
  /** Test seam. Defaults to the browser EventSource with credentials. */
  createEventSource?: EventSourceFactory;
}

export interface UseTaskEventsResult extends TaskEventsState {
  /** Re-open the stream (e.g. after a retry moved a FAILED task back to QUEUED). */
  restart: () => void;
}

/**
 * Subscribes to `GET /api/v1/agent/tasks/{id}/events` (SSE).
 * Mount under a component keyed by `taskId` so state resets when the task changes.
 */
export function useTaskEvents(taskId: string, options: UseTaskEventsOptions = {}): UseTaskEventsResult {
  const { enabled = true, createEventSource = browserEventSourceFactory } = options;
  const queryClient = useQueryClient();
  const [state, dispatch] = useReducer(taskEventsReducer, initialTaskEventsState);
  const [epoch, setEpoch] = useState(0);
  const factoryRef = useRef(createEventSource);

  useEffect(() => {
    factoryRef.current = createEventSource;
  }, [createEventSource]);

  useEffect(() => {
    if (!enabled) return;
    const invalidateTask = () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.tasks.detail(taskId) });
      void queryClient.invalidateQueries({ queryKey: queryKeys.applications.all });
      void queryClient.invalidateQueries({ queryKey: queryKeys.approvals.all });
      void queryClient.invalidateQueries({ queryKey: queryKeys.dashboard });
      void queryClient.invalidateQueries({ queryKey: queryKeys.notifications.all });
    };

    const stream = new TaskEventStream({
      url: taskEventsStreamPath(taskId),
      createEventSource: (url) => factoryRef.current(url),
      fetchHistory: (afterSequence, limit) => getTaskEventHistory(taskId, afterSequence, limit),
      onEvents: (events) => {
        dispatch({ type: "received", events });
        if (events.some((e) => STATUS_CHANGING_EVENT_TYPES.has(e.type))) invalidateTask();
      },
      onConnectionChange: (connection, attempt) => dispatch({ type: "connection", state: connection, attempt }),
      // Refetching the task goes through the API client, which refreshes an expired session
      // before the next EventSource is opened. Stop reconnecting once the task is terminal.
      beforeReconnect: async () => {
        try {
          const task = await queryClient.fetchQuery({
            queryKey: queryKeys.tasks.detail(taskId),
            queryFn: () => getTask(taskId),
            staleTime: 0,
          });
          return !TERMINAL_TASK_STATUSES.has(task.status);
        } catch (error) {
          return !(isApiError(error) && (error.status === 404 || error.status === 401));
        }
      },
    });
    stream.start();
    return () => stream.stop();
  }, [taskId, enabled, epoch, queryClient]);

  const restart = useCallback(() => setEpoch((n) => n + 1), []);
  return { ...state, restart };
}
