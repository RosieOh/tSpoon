import clsx from "clsx";
import type { ComponentProps, ReactNode } from "react";

const fieldBase =
  "w-full rounded-field px-4 text-ink outline-none transition-shadow focus-visible:outline-none focus:shadow-[inset_0_0_0_2px_var(--primary)] aria-[invalid=true]:shadow-[inset_0_0_0_2px_var(--danger)] disabled:text-ink-3";

// 흰 면 위에서는 회색 칸, 회색 바탕 위에서는 흰 칸
const fieldTone = (onBg?: boolean) => (onBg ? "bg-surface" : "bg-fill");

function FieldShell({
  label,
  hint,
  error,
  children,
  className,
}: {
  label?: ReactNode;
  hint?: ReactNode;
  error?: string | null;
  children: ReactNode;
  className?: string;
}) {
  return (
    <label className={clsx("block", className)}>
      {label && <span className="mb-2 block text-t7 font-medium text-ink-3">{label}</span>}
      {children}
      {error ? (
        <span role="alert" className="mt-1.5 block text-t7 text-danger-ink">
          {error}
        </span>
      ) : (
        hint && <span className="mt-1.5 block text-t7 text-ink-4">{hint}</span>
      )}
    </label>
  );
}

type FieldProps = {
  label?: ReactNode;
  hint?: ReactNode;
  error?: string | null;
  wrapperClassName?: string;
  /** 회색 바탕 위에 바로 놓일 때 */
  onBg?: boolean;
};

export function TextField({
  label,
  hint,
  error,
  wrapperClassName,
  onBg,
  className,
  ...rest
}: FieldProps & ComponentProps<"input">) {
  return (
    <FieldShell label={label} hint={hint} error={error} className={wrapperClassName}>
      <input
        aria-invalid={error ? true : undefined}
        className={clsx(fieldBase, fieldTone(onBg), "h-14 text-t4", className)}
        {...rest}
      />
    </FieldShell>
  );
}

export function TextArea({
  label,
  hint,
  error,
  wrapperClassName,
  onBg,
  className,
  ...rest
}: FieldProps & ComponentProps<"textarea">) {
  return (
    <FieldShell label={label} hint={hint} error={error} className={wrapperClassName}>
      <textarea
        aria-invalid={error ? true : undefined}
        className={clsx(fieldBase, fieldTone(onBg), "min-h-36 resize-none py-3.5 text-t5 leading-relaxed [field-sizing:content]", className)}
        {...rest}
      />
    </FieldShell>
  );
}

/** 글쓰기 화면용 — 테두리 없이 큰 글자로 */
export function BareTitleInput({ className, ...rest }: ComponentProps<"input">) {
  return (
    <input
      className={clsx(
        "w-full bg-transparent text-t2 font-bold text-ink outline-none placeholder:font-bold focus-visible:outline-none",
        className,
      )}
      {...rest}
    />
  );
}

export function BareTextArea({ className, ...rest }: ComponentProps<"textarea">) {
  return (
    <textarea
      className={clsx(
        "min-h-64 w-full resize-none bg-transparent text-body text-ink-2 outline-none [field-sizing:content] focus-visible:outline-none",
        className,
      )}
      {...rest}
    />
  );
}

/** iOS 스위치 */
export function Switch({
  checked,
  onChange,
  label,
  description,
}: {
  checked: boolean;
  onChange: (checked: boolean) => void;
  label: string;
  description?: string;
}) {
  return (
    <label className="flex cursor-pointer items-center justify-between gap-4 py-2">
      <span>
        <span className="block text-t5 font-semibold text-ink">{label}</span>
        {description && <span className="mt-0.5 block text-t7 text-ink-3">{description}</span>}
      </span>
      <span className="relative inline-flex shrink-0">
        <input
          type="checkbox"
          role="switch"
          checked={checked}
          onChange={(e) => onChange(e.target.checked)}
          className="peer absolute inset-0 cursor-pointer opacity-0"
        />
        <span
          aria-hidden
          className="h-[31px] w-[51px] rounded-full bg-fill-strong transition-colors duration-200 peer-checked:bg-positive peer-focus-visible:outline-2 peer-focus-visible:outline-offset-2 peer-focus-visible:outline-primary"
        />
        <span
          aria-hidden
          className="pointer-events-none absolute top-[2px] left-[2px] size-[27px] rounded-full bg-white shadow-[0_3px_8px_rgb(0_0_0/0.15),0_1px_1px_rgb(0_0_0/0.16)] transition-transform duration-200 ease-[var(--ease-spring)] peer-checked:translate-x-5"
        />
      </span>
    </label>
  );
}
