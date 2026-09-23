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
    <div className="detail-panel-backdrop" role="presentation" onClick={onClose}>
      <aside
        className="detail-panel"
        role="dialog"
        aria-modal="true"
        aria-labelledby="detail-panel-title"
        onClick={(event) => event.stopPropagation()}
      >
        <header className="detail-panel-header">
          <div>
            {eyebrow && <p className="eyebrow">{eyebrow}</p>}
            <h2 id="detail-panel-title">{title}</h2>
          </div>
          <button className="icon-button" onClick={onClose} aria-label="Đóng">
            <X size={18} />
          </button>
        </header>
        <div className="detail-panel-body">{children}</div>
        {footer && <footer className="detail-panel-footer">{footer}</footer>}
      </aside>
    </div>
  );
}
