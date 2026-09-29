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
    <div className="rounded-md bg-surface px-[23px] py-[45px] text-center min-[761px]:px-[45px] min-[761px]:py-[60px]">
      <span className="inline-grid h-[55px] w-[55px] place-items-center rounded-[4px] bg-brand-soft text-brand">
        <Check size={25} />
      </span>
      <h1 className="m-0 text-[35px] leading-[1.08] tracking-[-0.07em] text-ink min-[761px]:text-[42px]">
        {title}
      </h1>
      {description && (
        <p className="mx-auto my-5 max-w-[430px] text-[13px] text-muted">{description}</p>
      )}
      {action}
    </div>
  );
}
