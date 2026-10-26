import clsx from "clsx";
import { ChevronRight } from "lucide-react";
import Link from "next/link";
import type { ReactNode } from "react";

/** 회색 바탕 위의 흰 블록. 제목과 "더보기"를 둘 수 있다 */
export function Section({
  title,
  description,
  more,
  children,
  className,
  bodyClassName,
}: {
  title?: ReactNode;
  description?: ReactNode;
  more?: { href: string; label?: string };
  children: ReactNode;
  className?: string;
  bodyClassName?: string;
}) {
  return (
    <section className={clsx("mx-3 overflow-hidden rounded-section bg-surface", className)}>
      {(title || more) && (
        <header className="flex items-end justify-between gap-3 px-5 pt-6 pb-2">
          <div className="min-w-0">
            {title && <h2 className="text-t3 font-bold text-ink">{title}</h2>}
            {description && <p className="mt-0.5 text-t6 text-ink-3">{description}</p>}
          </div>
          {more && (
            <Link
              href={more.href}
              className="press -mr-1 flex shrink-0 items-center rounded-lg px-1 py-0.5 text-t6 font-medium text-ink-3"
            >
              {more.label ?? "더보기"}
              <ChevronRight className="size-4" aria-hidden />
            </Link>
          )}
        </header>
      )}
      <div className={clsx("pb-2", bodyClassName)}>{children}</div>
    </section>
  );
}
