"use client";

import { usePathname } from "next/navigation";
import { useEffect, useRef } from "react";

// 앱 안에서 화면을 옮긴 적이 있는지. 바로 들어온 상세 화면의 뒤로 가기가 앱 밖으로 나가지 않게 한다
let inAppNavigations = 0;

export function canGoBackInApp() {
  return inAppNavigations > 0;
}

export function NavigationTracker() {
  const pathname = usePathname();
  const first = useRef(true);
  useEffect(() => {
    if (first.current) {
      first.current = false;
      return;
    }
    inAppNavigations += 1;
  }, [pathname]);
  return null;
}
