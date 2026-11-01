"use client";

import { useEffect, useState } from "react";
import { apiBlob } from "@/lib/api/client";

type State = { key: string | null; url: string | null; error: unknown; loading: boolean };

/**
 * Loads a protected binary (screenshot, receipt artifact) through the API client so that an
 * expired access token is refreshed, and exposes it as an object URL that is revoked on change.
 */
export function useAuthedObjectUrl(path: string | null): { url: string | null; error: unknown; loading: boolean } {
  const [state, setState] = useState<State>({ key: null, url: null, error: null, loading: false });

  useEffect(() => {
    if (!path) return;
    let cancelled = false;
    let objectUrl: string | null = null;
    const controller = new AbortController();
    apiBlob(path, { signal: controller.signal })
      .then(({ blob }) => {
        if (cancelled) return;
        objectUrl = URL.createObjectURL(blob);
        setState({ key: path, url: objectUrl, error: null, loading: false });
      })
      .catch((error: unknown) => {
        if (!cancelled) setState({ key: path, url: null, error, loading: false });
      });
    return () => {
      cancelled = true;
      controller.abort();
      if (objectUrl) URL.revokeObjectURL(objectUrl);
    };
  }, [path]);

  if (!path) return { url: null, error: null, loading: false };
  if (state.key !== path) return { url: null, error: null, loading: true };
  return state;
}
