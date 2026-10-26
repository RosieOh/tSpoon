"use client";

import clsx from "clsx";
import { motion } from "motion/react";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { LiquidGlass } from "@/components/glass/LiquidGlass";
import { useOverlay } from "@/components/overlays/Overlays";
import { useSession } from "@/lib/queries/session";
import { actionVisible, isTabRoot, TAB_ACTIONS, TABS } from "./nav";

/**
 * 모바일 하단의 떠 있는 유리 탭바 (iOS 26).
 * 선택 버블이 스프링으로 미끄러지고, 화면의 주요 동작은 오른쪽 유리 원 버튼으로 분리한다.
 */
export function TabBar() {
  const pathname = usePathname();
  const router = useRouter();
  const { user } = useSession();
  const { requireAuth } = useOverlay();

  if (!isTabRoot(pathname)) return null;
  const action = TAB_ACTIONS[pathname];
  const showAction = actionVisible(action, user?.role);

  return (
    <div className="column-fixed pointer-events-none bottom-0 z-40 flex items-center gap-2.5 px-4 pb-[max(14px,env(safe-area-inset-bottom))] lg:hidden">
      <LiquidGlass
        as="nav"
        aria-label="주요 메뉴"
        radius={32}
        bezel={22}
        strength={64}
        blur={8}
        className="pointer-events-auto flex h-16 flex-1 items-center px-[5px]"
      >
        {TABS.map((tab) => {
          const active = tab.href === pathname;
          const Icon = tab.icon;
          return (
            <Link
              key={tab.href}
              href={tab.href}
              aria-current={active ? "page" : undefined}
              className={clsx(
                "relative z-[2] flex h-[54px] flex-1 flex-col items-center justify-center gap-[3px] rounded-[27px] transition-colors active:scale-95",
                active ? "text-primary-ink" : "text-ink-4",
              )}
              style={{ transitionProperty: "color, transform", transitionDuration: "180ms" }}
            >
              {active && (
                <motion.span
                  layoutId="tab-bubble"
                  className="absolute inset-0 rounded-[27px] bg-[color-mix(in_oklab,var(--primary)_13%,transparent)] shadow-[inset_0_1px_0_rgb(255_255_255/0.35)]"
                  transition={{ type: "spring", stiffness: 420, damping: 30, mass: 0.9 }}
                />
              )}
              <Icon className="relative size-[22px]" strokeWidth={active ? 2.3 : 1.9} aria-hidden />
              <span className="relative text-[10.5px] leading-none font-semibold">{tab.label}</span>
            </Link>
          );
        })}
      </LiquidGlass>

      {showAction && action && (
        <LiquidGlass
          as="button"
          type="button"
          aria-label={action.label}
          radius={32}
          bezel={22}
          strength={64}
          blur={8}
          onClick={() => {
            if (requireAuth(action.reason)) router.push(action.href);
          }}
          className="press pointer-events-auto grid size-16 shrink-0 place-items-center text-primary-ink"
        >
          <action.icon className="relative z-[2] size-6" strokeWidth={2.3} aria-hidden />
        </LiquidGlass>
      )}
    </div>
  );
}
