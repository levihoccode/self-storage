import { Moon, Sun } from "lucide-react";

export type Theme = "dark" | "light";

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
      className={className ? `theme-toggle ${className}` : "theme-toggle"}
      type="button"
      role="switch"
      aria-checked={isLight}
      aria-label={isLight ? "Chuyển sang giao diện tối" : "Chuyển sang giao diện sáng"}
      title={isLight ? "Giao diện tối" : "Giao diện sáng"}
      onClick={onToggle}
    >
      <span className="theme-toggle-track" aria-hidden="true">
        <span className="theme-toggle-thumb">
          {isLight ? <Moon size={13} strokeWidth={2.2} /> : <Sun size={13} strokeWidth={2.2} />}
        </span>
      </span>
    </button>
  );
}
