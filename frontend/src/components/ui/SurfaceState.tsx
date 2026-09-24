import { AlertTriangle, Inbox, Loader2, Lock, LogOut, SearchX } from "lucide-react";
import type { ReactNode } from "react";

export type SurfaceStateVariant =
  "loading" | "empty" | "error" | "forbidden" | "session-expired" | "not-found";

const ICON_BY_VARIANT: Record<SurfaceStateVariant, ReactNode> = {
  loading: <Loader2 className="surface-state-spin" size={28} />,
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
  return (
    <div
      className={`surface-state surface-state-${variant}`}
      role={variant === "error" || variant === "forbidden" ? "alert" : "status"}
    >
      <div className="surface-state-icon">{ICON_BY_VARIANT[variant]}</div>
      <p className="surface-state-title">{title}</p>
      {description && <p className="surface-state-description">{description}</p>}
      {action && (
        <button className="button button-secondary" onClick={action.onClick}>
          {action.label}
        </button>
      )}
    </div>
  );
}
