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
    <div
      className="fixed inset-0 z-[90] grid place-items-center bg-[rgba(7,16,19,0.68)] p-5"
      role="presentation"
      onClick={onCancel}
    >
      <div
        className="w-[min(420px,100%)] rounded-md border border-border bg-surface p-7 shadow-soft-token"
        role="alertdialog"
        aria-modal="true"
        aria-labelledby="confirm-dialog-title"
        onClick={(event) => event.stopPropagation()}
      >
        <h2
          id="confirm-dialog-title"
          className="mb-[10px] mt-0 text-[19px] leading-[1.2] tracking-[-0.03em]"
        >
          {title}
        </h2>
        {description && (
          <p className="mb-[22px] mt-0 text-[13px] leading-normal text-muted">{description}</p>
        )}
        <div className="flex justify-end gap-2.5">
          <button
            className="inline-flex min-h-11 items-center justify-center rounded-sm border border-brand bg-transparent px-[18px] text-label text-brand transition-colors hover:bg-brand hover:text-background disabled:cursor-not-allowed disabled:opacity-60"
            onClick={onCancel}
            disabled={isPending}
          >
            {cancelLabel}
          </button>
          <button
            className={
              tone === "danger"
                ? "inline-flex min-h-11 items-center justify-center gap-2 rounded-sm border-0 bg-danger px-[18px] text-label text-white transition-[filter] hover:brightness-[0.92] disabled:cursor-not-allowed disabled:opacity-60"
                : "inline-flex min-h-11 items-center justify-center gap-2 rounded-sm border-0 bg-brand px-[18px] text-label text-background transition-colors hover:bg-brand-strong disabled:cursor-not-allowed disabled:opacity-60"
            }
            onClick={onConfirm}
            disabled={isPending}
          >
            {isPending && (
              <Loader2 className="animate-[surface-state-spin_0.9s_linear_infinite]" size={14} />
            )}
            {confirmLabel}
          </button>
        </div>
      </div>
    </div>
  );
}
