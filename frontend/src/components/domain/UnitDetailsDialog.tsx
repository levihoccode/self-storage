import { useEffect } from "react";
import { ArrowRight, X } from "lucide-react";
import { formatPrice, UnitType } from "../../mocks/catalog";
import { Navigate } from "../../app/types";

export function UnitDetailsDialog({
  unit,
  onClose,
  navigate,
}: {
  unit: UnitType;
  onClose: () => void;
  navigate: Navigate;
}) {
  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") onClose();
    };
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, [onClose]);
  return (
    <div
      className="dialog-backdrop"
      role="presentation"
      onMouseDown={(event) => {
        if (event.target === event.currentTarget) onClose();
      }}
    >
      <div
        className="detail-dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby="unit-dialog-title"
      >
        <button className="dialog-close icon-button" onClick={onClose} aria-label="Đóng">
          <X size={19} />
        </button>
        <div className={`dialog-visual accent-${unit.accent}`}>
          <img className="dialog-image" src={unit.image} alt={`Hình ảnh tại kho ${unit.name}`} />
        </div>
        <div className="dialog-content">
          <p className="eyebrow">{unit.name}</p>
          <h2 id="unit-dialog-title">
            Phương án lưu trữ
            <br />
            cho {unit.idealFor.toLowerCase()}.
          </h2>
          <p>{unit.description}</p>
          <div className="dialog-specs">
            <span>
              <small>Quy mô</small>
              <strong>{unit.capacity}</strong>
            </span>
            <span>
              <small>Thông số</small>
              <strong>{unit.size}</strong>
            </span>
            <span>
              <small>Từ</small>
              <strong>{formatPrice(unit.monthlyPrice)}đ/tháng</strong>
            </span>
          </div>
          <button
            className="button button-primary button-full"
            onClick={() => {
              onClose();
              navigate("/rental-requests/new");
            }}
          >
            Gửi yêu cầu loại kho này <ArrowRight size={16} />
          </button>
        </div>
      </div>
    </div>
  );
}
