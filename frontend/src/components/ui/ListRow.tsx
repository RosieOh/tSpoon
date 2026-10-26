import clsx from "clsx";
import { ChevronRight } from "lucide-react";
import Link from "next/link";
import type { ReactNode } from "react";

type Props = {
  left?: ReactNode;
  title: ReactNode;
  description?: ReactNode;
  right?: ReactNode;
  chevron?: boolean;
  href?: string;
  onClick?: () => void;
  className?: string;
  titleClassName?: string;
};

/** 토스식 리스트 한 줄: 아이콘 · 제목/설명 · 오른쪽 값 */
export function ListRow({ left, title, description, right, chevron, href, onClick, className, titleClassName }: Props) {
  const body = (
    <>
      {left}
      <span className="min-w-0 flex-1">
        <span className={clsx("block truncate text-t5 font-semibold text-ink", titleClassName)}>{title}</span>
        {description && <span className="mt-0.5 block text-t6 text-ink-3">{description}</span>}
      </span>
      {right && <span className="shrink-0 text-right">{right}</span>}
      {chevron && <ChevronRight className="size-5 shrink-0 text-ink-5" aria-hidden />}
    </>
  );
  const base = clsx("flex w-full items-center gap-3.5 px-5 py-3.5 text-left", className);

  if (href) {
    return (
      <Link href={href} className={clsx(base, "press-row")}>
        {body}
      </Link>
    );
  }
  if (onClick) {
    return (
      <button type="button" onClick={onClick} className={clsx(base, "press-row")}>
        {body}
      </button>
    );
  }
  return <div className={base}>{body}</div>;
}
