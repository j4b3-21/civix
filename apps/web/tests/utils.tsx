import * as React from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, type RenderOptions } from "@testing-library/react";

export function createTestQueryClient() {
  return new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: Infinity }, mutations: { retry: false } } });
}

export function renderWithClient(ui: React.ReactElement, options?: RenderOptions & { client?: QueryClient }) {
  const client = options?.client ?? createTestQueryClient();
  const result = render(<QueryClientProvider client={client}>{ui}</QueryClientProvider>, options);
  return { ...result, client };
}
