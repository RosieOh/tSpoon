import clsx from "clsx";
import Link from "next/link";
import type { ComponentProps, ReactNode } from "react";
import { Spinner } from "./Spinner";

type Variant = "primary" | "weak" | "neutral" | "surface" | "danger" | "ghost";
type Size = "lg" | "md" | "sm";

const variants: Record<Variant, string> = {
  primary: "bg-primary text-white active:bg-primary-pressed",
  weak: "bg-primary-weak text-primary-weak-ink",
  // 흰 면(섹션·시트) 위의 보조 버튼
  neutral: "bg-fill text-ink-2",
  // 회색 바탕(화면 아래 CTA 자리) 위의 보조 버튼
  surface: "bg-surface text-ink-2",
  danger: "bg-danger-weak text-danger-ink",
  ghost: "text-ink-3",
};

const sizes: Record<Size, string> = {
  lg: "h-14 rounded-button px-5 text-t4 font-semibold",
  md: "h-12 rounded-[14px] px-4 text-t5 font-semibold",
  sm: "h-9 rounded-[10px] px-3 text-t6 font-semibold",
};

type Common = {
  variant?: Variant;
  size?: Size;
  block?: boolean;
  loading?: boolean;
  icon?: ReactNode;
};

function classes({ variant = "primary", size = "lg", block }: Common, className?: string) {
  return clsx(
    "press inline-flex select-none items-center justify-center gap-1.5 whitespace-nowrap disabled:cursor-not-allowed disabled:opacity-40",
    variants[variant],
    sizes[size],
    block && "w-full",
    className,
  );
}

export function Button({
  variant,
  size,
  block,
  loading,
  icon,
  className,
  children,
  disabled,
  type = "button",
  ...rest
}: Common & ComponentProps<"button">) {
  return (
    <button
      type={type}
      className={classes({ variant, size, block }, className)}
      disabled={disabled || loading}
      aria-busy={loading || undefined}
      {...rest}
    >
      {loading ? <Spinner className="size-5" /> : icon}
      {children}
    </button>
  );
}

export function ButtonLink({
  variant,
  size,
  block,
  icon,
  className,
  children,
  ...rest
}: Common & ComponentProps<typeof Link>) {
  return (
    <Link className={classes({ variant, size, block }, className)} {...rest}>
      {icon}
      {children}
    </Link>
  );
}
