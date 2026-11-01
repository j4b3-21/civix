import { apiFetch, apiPath } from "@/lib/api/client";
import type { AgentTaskDto, CitizenInputRequest, ExecutionEventDto, Page } from "@/types/api";

const id = (taskId: string) => encodeURIComponent(taskId);

export const listTasks = (params: { page?: number; size?: number } = {}) => apiFetch<Page<AgentTaskDto>>("/agent/tasks", { query: params });

export const getTask = (taskId: string) => apiFetch<AgentTaskDto>(`/agent/tasks/${id(taskId)}`);

export const taskEventsStreamPath = (taskId: string) => apiPath(`/agent/tasks/${id(taskId)}/events`);

export const getTaskEventHistory = (taskId: string, afterSequence = 0, limit = 200) =>
  apiFetch<ExecutionEventDto[]>(`/agent/tasks/${id(taskId)}/events/history`, { query: { afterSequence, limit } });

export const cancelTask = (taskId: string, reason?: string) =>
  apiFetch<AgentTaskDto>(`/agent/tasks/${id(taskId)}/cancel`, { method: "POST", body: reason ? { reason } : {} });

/** The value is sent once and never stored by the client (or by the server, per the contract). */
export const submitCitizenInput = (taskId: string, body: CitizenInputRequest) =>
  apiFetch<void>(`/agent/tasks/${id(taskId)}/citizen-input`, { method: "POST", body });

export const retryTask = (taskId: string) => apiFetch<void>(`/agent/tasks/${id(taskId)}/retry`, { method: "POST" });

export const screenshotPath = (taskId: string, screenshotId: string) =>
  `/agent/tasks/${id(taskId)}/screenshots/${encodeURIComponent(screenshotId)}`;
