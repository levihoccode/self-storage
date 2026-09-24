import { AlertTriangle, Inbox, Loader2, Lock, LogOut, SearchX } from "lucide-react";
import type { ReactNode } from "react";

export type SurfaceStateVariant =
  "loading" | "empty" | "error" | "forbidden" | "session-expired" | "not-found";

const SPIN = "animate-[surface-state-spin_0.9s_linear_infinite]";

const ICON_BY_VARIANT: Record<SurfaceStateVariant, ReactNode> = {
  loading: <Loader2 className={SPIN} size={28} />,
  empty: <Inbox size={28} />,
  error: <AlertTriangle size={28} />,
  forbidden: <Lock size={28} />,
  "session-expired": <LogOut size={28} />,
  "not-found": <SearchX size={28} />,
};

type SurfaceStateAction = {
  label: string;
  onClick: () => void;
};

/**
 * Shared "whole surface" state: renders where a page/section has nothing else to
 * show (loading, empty list, fetch error, forbidden, expired session, 404).
 * Every FM/Customer surface should reuse this instead of a page-specific block.
 */
export function SurfaceState({
  variant,
  title,
  description,
  action,
}: {
  variant: SurfaceStateVariant;
  title: string;
  description?: string;
  action?: SurfaceStateAction;
}) {
  const isNegative = variant === "error" || variant === "forbidden";
  return (
    <div
      className="grid justify-items-center gap-2.5 rounded-[14px] border border-border bg-surface px-6 py-14 text-center"
      role={isNegative ? "alert" : "status"}
    >
      <div
        className={`grid h-[52px] w-[52px] place-items-center rounded-full bg-surface-subtle ${isNegative ? "text-danger" : "text-muted"}`}
      >
        {ICON_BY_VARIANT[variant]}
      </div>
      <p className="m-0 text-[15px] font-bold text-ink">{title}</p>
      {description && (
        <p className="-mt-1 max-w-[380px] text-xs leading-normal text-muted">{description}</p>
      )}
      {action && (
        <button className="button button-secondary mt-2" onClick={action.onClick}>
          {action.label}
        </button>
      )}
    </div>
  );
}
