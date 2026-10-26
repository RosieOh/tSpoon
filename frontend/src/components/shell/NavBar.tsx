"use client";

import clsx from "clsx";
import { Bell, ChevronLeft, X } from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import type { ComponentProps, CSSProperties, ReactNode } from "react";
import { LiquidGlass } from "@/components/glass/LiquidGlass";
import { Avatar } from "@/components/ui/Avatar";
import { useScrolled } from "@/lib/hooks";
import { useUnreadCount } from "@/lib/queries/notifications";
import { useSession } from "@/lib/queries/session";
import { canGoBackInApp } from "./nav-history";

/*
 * 위쪽 내비게이션 (iOS 26).
 * 막대를 깔지 않고, 스크롤하면 내용이 위 가장자리에서 흐려지며 사라지는 "scroll edge" 만 보인다.
 * 버튼은 각각 떠 있는 유리 원.
 */
export function NavBar({
  left,
  right,
  title,
  alwaysShowTitle = false,
  overlay = false,
}: {
  left?: ReactNode;
  right?: ReactNode;
  title?: ReactNode;
  alwaysShowTitle?: boolean;
  /** 히어로 이미지 위에 겹쳐 놓을 때 — 자리를 차지하지 않는다 */
  overlay?: boolean;
}) {
  const scrolled = useScrolled(overlay ? 160 : 24);
  return (
    <div className={clsx("sticky top-0 z-30 h-14", overlay && "-mb-14")}>
      <div
        aria-hidden
        className={clsx(
          "pointer-events-none absolute inset-x-0 top-0 h-[88px] transition-opacity duration-300",
          scrolled ? "opacity-100" : "opacity-0",
        )}
        style={
          {
            background: "linear-gradient(to bottom, color-mix(in oklab, var(--bg) 92%, transparent) 30%, transparent)",
            backdropFilter: "blur(10px)",
            WebkitBackdropFilter: "blur(10px)",
            maskImage: "linear-gradient(to bottom, black 45%, transparent)",
            WebkitMaskImage: "linear-gradient(to bottom, black 45%, transparent)",
          } as CSSProperties
        }
      />
      {/* 겹침 모드: 데스크톱에서는 히어로가 둥근 카드로 내려앉으므로 버튼도 그 안쪽으로 들인다 */}
      <div
        className={clsx(
          "relative flex h-14 items-center justify-between gap-2 px-3",
          overlay && "lg:translate-y-6 lg:px-6",
        )}
      >
        <div className="flex min-w-11 items-center gap-2">{left}</div>
        {title && (
          <div
            className={clsx(
              "pointer-events-none absolute inset-x-20 truncate text-center text-t4 font-semibold text-ink transition-opacity duration-200",
              alwaysShowTitle || scrolled ? "opacity-100" : "opacity-0",
            )}
          >
            {title}
          </div>
        )}
        <div className="flex min-w-11 items-center justify-end gap-2">{right}</div>
      </div>
    </div>
  );
}

type GlassButtonProps = {
  label: string;
  children: ReactNode;
  badge?: boolean;
  className?: string;
};

/** 떠 있는 유리 원 버튼 */
export function GlassIconButton({
  label,
  children,
  badge,
  className,
  href,
  ...rest
}: GlassButtonProps & ({ href: string } | ({ href?: undefined } & ComponentProps<"button">))) {
  const inner = (
    <>
      <span className="relative z-[2] grid place-items-center">{children}</span>
      {badge && (
        <span className="absolute top-2 right-2 z-[3] size-2 rounded-full bg-danger ring-2 ring-[var(--glass-tint-strong)]" />
      )}
    </>
  );
  const classes = clsx("press grid size-11 shrink-0 place-items-center text-ink", className);
  if (href) {
    return (
      <LiquidGlass as={Link} href={href} aria-label={label} radius={22} bezel={14} strength={36} className={classes}>
        {inner}
      </LiquidGlass>
    );
  }
  return (
    <LiquidGlass
      as="button"
      type="button"
      aria-label={label}
      radius={22}
      bezel={14}
      strength={36}
      className={classes}
      {...(rest as ComponentProps<"button">)}
    >
      {inner}
    </LiquidGlass>
  );
}

export function BackButton({ fallback = "/", close = false }: { fallback?: string; close?: boolean }) {
  const router = useRouter();
  return (
    <GlassIconButton
      label={close ? "닫기" : "뒤로 가기"}
      onClick={() => (canGoBackInApp() ? router.back() : router.push(fallback))}
    >
      {close ? <X className="size-[22px]" aria-hidden /> : <ChevronLeft className="size-6" aria-hidden />}
    </GlassIconButton>
  );
}

/** 탭 화면 오른쪽 위 — 알림과 내 정보. 데스크톱에서는 사이드바에 있으므로 숨긴다 */
export function AccountActions() {
  const { user, isLoading } = useSession();
  const unread = useUnreadCount().data ?? 0;
  if (isLoading) return <span className="size-11" />;
  if (!user) {
    return (
      <LiquidGlass
        as={Link}
        href="/login"
        radius={22}
        bezel={14}
        strength={36}
        className="press flex h-11 items-center px-4 text-t6 font-semibold text-ink lg:hidden"
      >
        <span className="relative z-[2]">로그인</span>
      </LiquidGlass>
    );
  }
  return (
    <div className="flex items-center gap-2 lg:hidden">
      <GlassIconButton href="/notifications" label={unread > 0 ? `알림, 안 읽은 알림 ${unread}개` : "알림"} badge={unread > 0}>
        <Bell className="size-[21px]" aria-hidden />
      </GlassIconButton>
      <Link href="/me" aria-label="내 정보" className="press rounded-full">
        <Avatar name={user.name} size={44} />
      </Link>
    </div>
  );
}

export function LargeTitle({ title, children }: { title: ReactNode; children?: ReactNode }) {
  return (
    <div className="px-5 pt-1 pb-4">
      <h1 className="text-t1 text-ink">{title}</h1>
      {children}
    </div>
  );
}
