import { Loader2 } from "lucide-react";

/**
 * Shared destructive/blocking confirmation dialog (return, cancel, delete, etc.).
 * FM queues and Customer panels both gate irreversible actions through this
 * instead of each surface rolling its own confirm modal.
 */
export function ConfirmDialog({
  title,
  description,
  confirmLabel,
  cancelLabel = "Hủy",
  tone = "danger",
  isPending = false,
  onConfirm,
  onCancel,
}: {
  title: string;
  description?: string;
  confirmLabel: string;
  cancelLabel?: string;
  tone?: "danger" | "default";
  isPending?: boolean;
  onConfirm: () => void;
  onCancel: () => void;
}) {
  return (
    <div className="confirm-backdrop" role="presentation" onClick={onCancel}>
      <div
        className="confirm-dialog"
        role="alertdialog"
        aria-modal="true"
        aria-labelledby="confirm-dialog-title"
        onClick={(event) => event.stopPropagation()}
      >
        <h2 id="confirm-dialog-title">{title}</h2>
        {description && <p>{description}</p>}
        <div className="confirm-dialog-actions">
          <button className="button button-secondary" onClick={onCancel} disabled={isPending}>
            {cancelLabel}
          </button>
          <button
            className={tone === "danger" ? "button button-danger" : "button button-primary"}
            onClick={onConfirm}
            disabled={isPending}
          >
            {isPending && <Loader2 className="surface-state-spin" size={14} />}
            {confirmLabel}
          </button>
        </div>
      </div>
    </div>
  );
}
