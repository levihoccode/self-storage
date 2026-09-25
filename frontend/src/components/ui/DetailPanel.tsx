import { X } from "lucide-react";
import type { ReactNode } from "react";

/**
 * Shared slide-over panel for record detail (Proposal, Invoice, Extend
 * request, Return request, Support request, Unit/contract...). The fe-pages
 * spec lists these as panels on an existing route, not separate pages, so
 * every role renders them through this one pattern instead of a bespoke
 * modal per record type.
 */
export function DetailPanel({
  title,
  eyebrow,
  onClose,
  children,
  footer,
}: {
  title: string;
  eyebrow?: string;
  onClose: () => void;
  children: ReactNode;
  footer?: ReactNode;
}) {
  return (
    <div
      className="fixed inset-0 z-[70] flex justify-end bg-[rgba(7,16,19,0.6)]"
      role="presentation"
      onClick={onClose}
    >
      <aside
        className="flex h-full w-[min(480px,100%)] flex-col border-l border-border bg-surface shadow-soft-token max-[760px]:w-full"
        role="dialog"
        aria-modal="true"
        aria-labelledby="detail-panel-title"
        onClick={(event) => event.stopPropagation()}
      >
        <header className="flex items-start justify-between gap-4 border-b border-border px-6 pb-4 pt-6">
          <div>
            {eyebrow && (
              <p className="font-mono text-mono uppercase tracking-[0.08em] text-brand">
                {eyebrow}
              </p>
            )}
            <h2 id="detail-panel-title" className="mt-1 text-[19px] tracking-[-0.03em]">
              {title}
            </h2>
          </div>
          <button
            className="grid h-[38px] w-[38px] place-items-center rounded-sm border border-border bg-surface text-ink transition-colors hover:border-brand hover:text-brand"
            onClick={onClose}
            aria-label="Đóng"
          >
            <X size={18} />
          </button>
        </header>
        <div className="flex-1 overflow-y-auto px-6 py-5">{children}</div>
        {footer && (
          <footer className="flex justify-end gap-2.5 border-t border-border px-6 py-4">
            {footer}
          </footer>
        )}
      </aside>
    </div>
  );
}
