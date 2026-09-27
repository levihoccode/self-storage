import { Moon, Sun } from "lucide-react";

export type Theme = "dark" | "light";

const ICON_ANIMATION = "animate-[theme-icon-in_280ms_cubic-bezier(0.16,1,0.3,1)]";

export function ThemeToggle({
  theme,
  onToggle,
  className,
}: {
  theme: Theme;
  onToggle: () => void;
  className?: string;
}) {
  const isLight = theme === "light";

  return (
    <button
      className={`inline-flex min-h-[34px] items-center gap-2 rounded-full border border-border bg-surface-subtle p-[4px_9px_4px_5px] text-[11px] font-[750] text-muted [transition:color_0.28s_ease,background-color_0.28s_ease,border-color_0.28s_ease] hover:border-brand hover:bg-brand-soft hover:text-ink ${className ?? ""}`}
      type="button"
      role="switch"
      aria-checked={isLight}
      aria-label={isLight ? "Chuyển sang giao diện tối" : "Chuyển sang giao diện sáng"}
      title={isLight ? "Giao diện tối" : "Giao diện sáng"}
      onClick={onToggle}
    >
      <span
        className="inline-flex h-[22px] w-[38px] items-center rounded-full border border-border bg-background p-0.5 [transition:background-color_0.28s_ease]"
        aria-hidden="true"
      >
        <span
          className={`grid h-4 w-4 place-items-center rounded-full bg-brand text-background [transition:transform_0.28s_cubic-bezier(0.16,1,0.3,1),color_0.28s_ease,background-color_0.28s_ease] ${isLight ? "translate-x-[18px]" : "translate-x-0"}`}
        >
          {isLight ? (
            <Moon size={13} strokeWidth={2.2} className={ICON_ANIMATION} />
          ) : (
            <Sun size={13} strokeWidth={2.2} className={ICON_ANIMATION} />
          )}
        </span>
      </span>
    </button>
  );
}
