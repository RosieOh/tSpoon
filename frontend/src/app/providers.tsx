"use client";

import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MotionConfig } from "motion/react";
import { useState, type ReactNode } from "react";
import { OverlayProvider } from "@/components/overlays/Overlays";
import { isApiError } from "@/lib/api";

export function Providers({ children }: { children: ReactNode }) {
  const [client] = useState(
    () =>
      new QueryClient({
        defaultOptions: {
          queries: {
            staleTime: 30_000,
            // 상세 조회가 조회수를 올리므로 창을 다시 볼 때마다 받지 않는다
            refetchOnWindowFocus: false,
            // 4xx 는 다시 해도 같은 결과
            retry: (count, error) => !(isApiError(error) && error.status >= 400 && error.status < 500) && count < 2,
          },
        },
      }),
  );
  return (
    <QueryClientProvider client={client}>
      {/* 동작 줄이기를 켠 사용자에게는 움직임 없이 바로 바뀐다 */}
      <MotionConfig reducedMotion="user">
        <OverlayProvider>{children}</OverlayProvider>
      </MotionConfig>
    </QueryClientProvider>
  );
}
