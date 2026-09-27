import { X } from "lucide-react";

export function ComingSoonDialog({ onClose }: { onClose: () => void }) {
  return (
    <div className="coming-soon-backdrop" role="presentation" onClick={onClose}>
      <div
        className="coming-soon-dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby="coming-soon-title"
        onClick={(event) => event.stopPropagation()}
      >
        <button className="coming-soon-close" onClick={onClose} aria-label="Đóng">
          <X size={18} />
        </button>
        <p className="eyebrow">Đang hoàn thiện</p>
        <h2 id="coming-soon-title">Chức năng đang được hoàn thiện</h2>
        <p>Tính năng này sẽ sớm có mặt trong không gian của bạn.</p>
        <button className="button button-primary" onClick={onClose}>
          Đã hiểu
        </button>
      </div>
    </div>
  );
}
