import clsx from "clsx";

export function Spinner({ className, label = "불러오는 중" }: { className?: string; label?: string }) {
  return (
    <span
      role="status"
      aria-label={label}
      className={clsx(
        "inline-block size-6 rounded-full border-[2.5px] border-current border-r-transparent opacity-80",
        className,
      )}
      style={{ animation: "spin 0.7s linear infinite" }}
    />
  );
}
