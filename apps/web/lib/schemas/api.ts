/**
 * Runtime validation for responses whose shape the UI depends on for
 * safety-relevant behaviour (auth, problem details, live events, approvals).
 * Objects are "loose" so that additive API changes never break the client.
 */
import { z } from "zod";
import {
  ACTION_CLASSES,
  AGENT_NAMES,
  APPROVAL_KINDS,
  APPROVAL_STATUSES,
  CITIZEN_INPUT_KINDS,
  EXECUTION_EVENT_TYPES,
  ROLES,
  TASK_STATUSES,
} from "@/types/api";

export const problemSchema = z.looseObject({
  type: z.string().optional(),
  title: z.string().optional(),
  status: z.number().optional(),
  detail: z.string().optional(),
  code: z.string().optional(),
  correlationId: z.string().optional(),
  errors: z.array(z.looseObject({ field: z.string(), message: z.string() })).optional(),
});

export const userSchema = z.looseObject({
  id: z.string(),
  email: z.string(),
  fullName: z.string(),
  roles: z.array(z.enum(ROLES)),
  createdAt: z.string(),
});

export const executionEventSchema = z.looseObject({
  eventId: z.string().min(1),
  taskId: z.string(),
  sequence: z.number().int().nonnegative(),
  type: z.enum(EXECUTION_EVENT_TYPES),
  timestamp: z.string(),
  agent: z.enum(AGENT_NAMES).nullable().optional().transform((v) => v ?? null),
  step: z.string().nullable().optional().transform((v) => v ?? null),
  message: z.string().default(""),
  metadata: z.record(z.string(), z.unknown()).nullable().optional().transform((v) => v ?? {}),
});

export const approvalSchema = z.looseObject({
  id: z.string(),
  taskId: z.string(),
  applicationId: z.string(),
  serviceName: z.string(),
  kind: z.enum(APPROVAL_KINDS),
  actionClass: z.enum(ACTION_CLASSES),
  summary: z.string(),
  details: z.looseObject({
    fields: z.array(z.looseObject({ label: z.string(), value: z.union([z.string(), z.number(), z.boolean(), z.null()]) })).default([]),
    documents: z
      .array(z.looseObject({ documentId: z.string(), category: z.string(), fileName: z.string().nullable().optional() }))
      .default([]),
    portal: z.string().nullable().optional().transform((v) => v ?? null),
  }),
  dataVersion: z.number().int(),
  status: z.enum(APPROVAL_STATUSES),
  createdAt: z.string(),
  expiresAt: z.string(),
  decidedAt: z.string().nullable(),
  decisionReason: z.string().nullable(),
});

export const agentTaskSchema = z.looseObject({
  id: z.string(),
  applicationId: z.string(),
  serviceName: z.string(),
  status: z.enum(TASK_STATUSES),
  currentStep: z.string().nullable(),
  currentAgent: z.enum(AGENT_NAMES).nullable(),
  pendingApproval: approvalSchema.nullable(),
  pendingCitizenInput: z
    .looseObject({
      requestId: z.string(),
      kind: z.enum(CITIZEN_INPUT_KINDS),
      prompt: z.string(),
      screenshotId: z.string().nullable().optional().transform((v) => v ?? null),
    })
    .nullable(),
  lastSequence: z.number().int(),
});

export type ParsedExecutionEvent = z.infer<typeof executionEventSchema>;
