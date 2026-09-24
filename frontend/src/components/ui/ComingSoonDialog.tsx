import { X } from "lucide-react";

export function ComingSoonDialog({ onClose }: { onClose: () => void }) {
  return (
    <div
      className="fixed inset-0 z-[80] grid place-items-center bg-[rgba(7,16,19,0.68)] p-5"
      role="presentation"
      onClick={onClose}
    >
      <div
        className="relative w-[min(420px,100%)] rounded-md border border-accent bg-surface p-8 shadow-[0_0_22px_rgba(53,133,142,0.45),var(--shadow-soft)]"
        role="dialog"
        aria-modal="true"
        aria-labelledby="coming-soon-title"
        onClick={(event) => event.stopPropagation()}
      >
        <button
          className="absolute right-[14px] top-[14px] grid h-[34px] w-[34px] place-items-center rounded-sm border border-border bg-transparent text-muted hover:border-accent hover:text-ink"
          onClick={onClose}
          aria-label="Đóng"
        >
          <X size={18} />
        </button>
        <p className="eyebrow">Đang hoàn thiện</p>
        <h2
          id="coming-soon-title"
          className="mb-3 mt-2 max-w-[300px] text-[26px] leading-[1.08] tracking-[-0.04em]"
        >
          Chức năng đang được hoàn thiện
        </h2>
        <p className="mb-6 text-[13px] text-muted">
          Tính năng này sẽ sớm có mặt trong không gian của bạn.
        </p>
        <button className="button button-primary w-full" onClick={onClose}>
          Đã hiểu
        </button>
      </div>
    </div>
  );
}
