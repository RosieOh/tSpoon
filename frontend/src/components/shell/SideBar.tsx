"use client";

import clsx from "clsx";
import { Bell } from "lucide-react";
import { motion } from "motion/react";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { BrandLogo } from "@/components/brand/BrandMark";
import { LiquidGlass } from "@/components/glass/LiquidGlass";
import { useOverlay } from "@/components/overlays/Overlays";
import { Avatar } from "@/components/ui/Avatar";
import { Button } from "@/components/ui/Button";
import { roleLabel } from "@/lib/labels";
import { useUnreadCount } from "@/lib/queries/notifications";
import { useSession } from "@/lib/queries/session";
import { actionVisible, activeTabHref, TAB_ACTIONS, TABS } from "./nav";

/** 데스크톱의 떠 있는 유리 사이드바 (macOS 26) */
export function SideBar() {
  return <SideBarFrame pathname={usePathname()} />;
}

export function SideBarFrame({ pathname }: { pathname: string | null }) {
  const router = useRouter();
  const { user } = useSession();
  const unread = useUnreadCount().data ?? 0;
  const { requireAuth } = useOverlay();
  const activeHref = activeTabHref(pathname);
  const action = activeHref ? TAB_ACTIONS[activeHref] : undefined;
  const showAction = actionVisible(action, user?.role);

  return (
    <LiquidGlass
      as="aside"
      radius={28}
      bezel={20}
      strength={48}
      className="fixed top-4 bottom-4 left-4 z-40 hidden w-[232px] flex-col p-3 lg:flex"
    >
      <Link href="/" aria-label="티스푼 홈" className="relative z-[2] rounded-2xl px-2.5 py-2.5">
        <BrandLogo size={30} />
      </Link>

      <nav aria-label="주요 메뉴" className="relative z-[2] mt-3 flex flex-col gap-0.5">
        {TABS.map((tab) => {
          const active = tab.href === activeHref;
          const Icon = tab.icon;
          return (
            <Link
              key={tab.href}
              href={tab.href}
              aria-current={active ? "page" : undefined}
              className={clsx(
                "press relative flex h-11 items-center gap-3 rounded-[14px] px-3 text-t5 font-semibold",
                active ? "text-primary-ink" : "text-ink-3",
              )}
            >
              {active && (
                <motion.span
                  layoutId="side-bubble"
                  className="absolute inset-0 rounded-[14px] bg-[color-mix(in_oklab,var(--primary)_12%,transparent)]"
                  transition={{ type: "spring", stiffness: 420, damping: 34 }}
                />
              )}
              <Icon className="relative size-5" strokeWidth={active ? 2.3 : 1.9} aria-hidden />
              <span className="relative">{tab.label}</span>
            </Link>
          );
        })}
      </nav>

      {showAction && action && (
        <Button
          size="md"
          block
          className="relative z-[2] mt-4"
          icon={<action.icon className="size-[18px]" aria-hidden />}
          onClick={() => {
            if (requireAuth(action.reason)) router.push(action.href);
          }}
        >
          {action.label}
        </Button>
      )}

      <div className="relative z-[2] mt-auto flex flex-col gap-0.5">
        {user ? (
          <>
            <Link
              href="/notifications"
              className={clsx(
                "press flex h-11 items-center gap-3 rounded-[14px] px-3 text-t5 font-semibold",
                pathname === "/notifications" ? "bg-[color-mix(in_oklab,var(--ink)_7%,transparent)] text-ink" : "text-ink-3",
              )}
            >
              <Bell className="size-5" strokeWidth={1.9} aria-hidden />
              알림
              {unread > 0 && (
                <span className="tabular ml-auto grid h-5 min-w-5 place-items-center rounded-full bg-danger px-1.5 text-t8 font-bold text-white">
                  {unread > 99 ? "99+" : unread}
                </span>
              )}
            </Link>
            <Link
              href="/me"
              className={clsx(
                "press flex items-center gap-3 rounded-[16px] p-2",
                pathname?.startsWith("/me") && "bg-[color-mix(in_oklab,var(--ink)_7%,transparent)]",
              )}
            >
              <Avatar name={user.name} size={36} />
              <span className="min-w-0">
                <span className="block truncate text-t6 font-semibold text-ink">{user.name}</span>
                <span className="block text-t8 text-ink-3">{roleLabel[user.role]}</span>
              </span>
            </Link>
          </>
        ) : (
          <Button size="md" variant="weak" block onClick={() => router.push("/login")}>
            로그인
          </Button>
        )}
      </div>
    </LiquidGlass>
  );
}
