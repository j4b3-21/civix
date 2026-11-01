"use client";

import { useParams } from "next/navigation";
import { ControlRoom } from "@/features/agent/components/control-room";

export default function AgentTaskPage() {
  const { taskId } = useParams<{ taskId: string }>();
  return <ControlRoom key={taskId} taskId={taskId} />;
}
