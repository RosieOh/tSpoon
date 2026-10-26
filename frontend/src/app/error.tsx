"use client";

import { Button } from "@/components/ui/Button";
import { ResultView } from "@/components/ui/Result";

export default function ErrorPage({ retry }: { error: Error & { digest?: string }; retry: () => void }) {
  return (
    <ResultView emoji="thinking" title="화면을 그리지 못했어요" description="잠시 후 다시 시도해 주세요">
      <Button variant="weak" size="md" className="mt-8" onClick={() => retry()}>
        다시 시도하기
      </Button>
    </ResultView>
  );
}
