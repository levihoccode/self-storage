import { Loader2 } from "lucide-react";
import type { ButtonHTMLAttributes } from "react";

export type ButtonVariant = "primary" | "secondary" | "onDark" | "danger";

const BASE =
  "inline-flex min-h-[42px] items-center justify-center gap-2 rounded-none border-2 px-4 text-button font-semibold transition-colors duration-150 ease disabled:cursor-not-allowed disabled:opacity-60";

const VARIANT_CLASS: Record<ButtonVariant, string> = {
  primary: "border-brand bg-brand text-signal-ink hover:border-brand-strong hover:bg-brand-strong",
  secondary:
    "border-control-border bg-transparent text-ink hover:border-control-border-hover hover:bg-control-bg-hover hover:text-control-hover-ink",
  onDark:
    "border-on-dark-border bg-transparent text-on-dark-ink hover:border-on-dark-border-hover hover:bg-on-dark-bg-hover",
  danger: "border-danger bg-transparent text-danger hover:bg-danger-soft",
};

/** Same visual class as <Button>, for the rare case a link (`<a>`) needs to
 * look like a button — <Button> itself always renders a native <button>. */
export function buttonClassName(variant: ButtonVariant = "primary") {
  return `${BASE} ${VARIANT_CLASS[variant]}`;
}

/**
 * Shared brand button. `pending` shows a spinner and disables the button in one
 * prop, instead of each page hand-rolling its own busy/disabled ternary.
 */
export function Button({
  variant = "primary",
  pending = false,
  className,
  children,
  disabled,
  type = "button",
  ...props
}: {
  variant?: ButtonVariant;
  pending?: boolean;
} & ButtonHTMLAttributes<HTMLButtonElement>) {
  return (
    <button
      type={type}
      className={`${BASE} ${VARIANT_CLASS[variant]}${className ? ` ${className}` : ""}`}
      disabled={disabled || pending}
      {...props}
    >
      {pending && (
        <Loader2 className="animate-[surface-state-spin_0.9s_linear_infinite]" size={16} />
      )}
      {children}
    </button>
  );
}
