import { Loader2 } from "lucide-react";
import type { ButtonHTMLAttributes } from "react";

export type ButtonVariant = "primary" | "secondary";

const BASE =
  "inline-flex min-h-11 items-center justify-center gap-2.5 rounded-sm px-[18px] text-[14px] font-[750] transition-colors duration-[180ms] ease disabled:cursor-not-allowed disabled:opacity-60";

const VARIANT_CLASS: Record<ButtonVariant, string> = {
  primary:
    "border border-transparent bg-brand text-background hover:bg-brand-strong hover:text-background",
  secondary: "border border-brand bg-transparent text-brand hover:bg-brand hover:text-background",
};

/** Same visual class as <Button>, for the rare case a link (`<a>`) needs to
 * look like a button — <Button> itself always renders a native <button>. */
export function buttonClassName(variant: ButtonVariant = "primary") {
  return `${BASE} ${VARIANT_CLASS[variant]}`;
}

/**
 * Shared brand button — replaces the near-identical PRIMARY_BUTTON /
 * SECONDARY_BUTTON class strings that used to be copy-pasted per page.
 * `pending` shows a spinner and disables the button in one prop, instead of
 * each page hand-rolling its own busy/disabled ternary.
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
