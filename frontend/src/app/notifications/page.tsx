"use client";

import clsx from "clsx";
import { useRouter } from "next/navigation";
import { LiquidGlass } from "@/components/glass/LiquidGlass";
import { useOverlay } from "@/components/overlays/Overlays";
import { BackButton, NavBar } from "@/components/shell/NavBar";
import { RelativeTime } from "@/components/ui/RelativeTime";
import { IconTile } from "@/components/ui/Emoji";
import { EmptyState, ErrorState, LoadMore, SkeletonRows } from "@/components/ui/States";
import { notificationType } from "@/lib/labels";
import { useMarkAllRead, useMarkRead, useNotifications, useUnreadCount } from "@/lib/queries/notifications";
import { flatten } from "@/lib/queries/paging";
import type { Notification } from "@/lib/types";

// 알림의 이동 주소는 앱 안 경로만 따른다
function safeInternalPath(url: string | null) {
  return url && url.startsWith("/") && !url.startsWith("//") ? url : null;
}

export default function NotificationsPage() {
  const notifications = useNotifications();
  const unread = useUnreadCount().data ?? 0;
  const markAll = useMarkAllRead();
  const { toast } = useOverlay();
  const items = flatten(notifications.data);
  const fresh = items.filter((n) => !n.read);
  const past = items.filter((n) => n.read);

  return (
    <>
      <NavBar
        left={<BackButton fallback="/" />}
        title="알림"
        alwaysShowTitle
        right={
          unread > 0 ? (
            <LiquidGlass
              as="button"
              type="button"
              radius={22}
              bezel={14}
              strength={36}
              onClick={() => markAll.mutate(undefined, { onSuccess: () => toast("모두 읽음으로 바꿨어요") })}
              className="press flex h-11 items-center px-4 text-t6 font-semibold text-ink"
            >
              <span className="relative z-[2]">모두 읽음</span>
            </LiquidGlass>
          ) : null
        }
      />
      {notifications.isPending ? (
        <div className="mx-3 mt-2 rounded-section bg-surface">
          <SkeletonRows count={5} />
        </div>
      ) : notifications.isError ? (
        <ErrorState error={notifications.error} onRetry={() => notifications.refetch()} />
      ) : items.length === 0 ? (
        <EmptyState emoji="mailbox" title="아직 알림이 없어요" description="내 글에 댓글이나 답변이 달리면 알려드릴게요" className="pt-24" />
      ) : (
        <div className="mt-2 space-y-3">
          {fresh.length > 0 && <Group title="새 알림" items={fresh} />}
          {past.length > 0 && <Group title="지난 알림" items={past} />}
          <LoadMore {...notifications} />
        </div>
      )}
    </>
  );
}

function Group({ title, items }: { title: string; items: Notification[] }) {
  return (
    <section className="mx-3 rounded-section bg-surface pt-5 pb-2" aria-label={title}>
      <h2 className="px-5 pb-1 text-t6 font-semibold text-ink-3">{title}</h2>
      <ul>
        {items.map((n) => (
          <li key={n.id}>
            <NotificationRow notification={n} />
          </li>
        ))}
      </ul>
    </section>
  );
}

function NotificationRow({ notification }: { notification: Notification }) {
  const router = useRouter();
  const markRead = useMarkRead();
  const visual = notificationType[notification.type];
  const target = safeInternalPath(notification.actionUrl);

  const open = () => {
    if (!notification.read) markRead.mutate(notification.id);
    if (target) router.push(target);
  };

  return (
    <button type="button" onClick={open} className="press-row flex w-full gap-3.5 px-5 py-3.5 text-left">
      <span className="relative">
        <IconTile emoji={visual.emoji} tint={visual.tint} size={44} />
        {!notification.read && (
          <span className="absolute -top-0.5 -right-0.5 size-2.5 rounded-full bg-primary ring-2 ring-surface" aria-label="안 읽음" />
        )}
      </span>
      <span className="min-w-0 flex-1">
        <span className={clsx("block text-t5 text-ink", notification.read ? "font-medium" : "font-bold")}>
          {notification.title}
        </span>
        <span className="mt-0.5 line-clamp-2 block text-t6 text-ink-3">{notification.content}</span>
        <span className="mt-1 block text-t7 text-ink-4"><RelativeTime iso={notification.createdAt} /></span>
      </span>
    </button>
  );
}
