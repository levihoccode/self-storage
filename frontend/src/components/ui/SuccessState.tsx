import { Check } from "lucide-react";
import type { ReactNode } from "react";

/**
 * Shared "success" state after a mutation completes (request submitted,
 * email verified, action confirmed). Extracted from RequestPage's inline
 * markup so Customer and FM surfaces reuse one pattern instead of
 * duplicating the success-panel markup per page.
 */
export function SuccessState({
  title,
  description,
  action,
}: {
  title: string;
  description?: string;
  action?: ReactNode;
}) {
  return (
    <div className="success-panel">
      <span className="success-icon">
        <Check size={25} />
      </span>
      <h1>{title}</h1>
      {description && <p>{description}</p>}
      {action}
    </div>
  );
}
