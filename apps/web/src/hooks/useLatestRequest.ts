import { useCallback, useEffect, useRef } from "react";

export type LatestRequestTicket = {
  signal: AbortSignal;
  isCurrent: () => boolean;
};

export function useLatestRequest() {
  const requestIdRef = useRef(0);
  const controllerRef = useRef<AbortController | null>(null);
  const mountedRef = useRef(true);

  useEffect(() => {
    mountedRef.current = true;
    return () => {
      mountedRef.current = false;
      controllerRef.current?.abort();
    };
  }, []);

  return useCallback((): LatestRequestTicket => {
    // 新请求发起前中止上一轮，筛选快速切换时只允许最后一次响应落状态。
    controllerRef.current?.abort();
    const controller = new AbortController();
    controllerRef.current = controller;
    const requestId = ++requestIdRef.current;

    return {
      signal: controller.signal,
      isCurrent: () => mountedRef.current && controllerRef.current === controller && requestIdRef.current === requestId,
    };
  }, []);
}
