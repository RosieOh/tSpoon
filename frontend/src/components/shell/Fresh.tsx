"use client";

import { useRouter } from "next/navigation";
import { Fragment, type ReactNode } from "react";

/**
 * 새로 들어올 때마다 처음 상태로 그린다. 뒤로·앞으로 가기로 돌아오면 쓰던 내용은 그대로 둔다.
 * (Cache Components 는 화면을 Activity 로 보존하므로, 작성 화면은 이걸로 감싼다)
 */
export function Fresh({ children }: { children: ReactNode }) {
  const { bfcacheId } = useRouter();
  return <Fragment key={bfcacheId}>{children}</Fragment>;
}
