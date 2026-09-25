import { useEffect } from "react";
import { ArrowRight, X } from "lucide-react";
import { formatPrice, UnitType } from "../../mocks/catalog";
import { Navigate } from "../../app/types";

const ACCENT_BACKGROUND: Record<UnitType["accent"], string> = {
  sand: "bg-unit-sand",
  teal: "bg-unit-teal",
  ink: "bg-unit-ink",
};

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
      className="fixed inset-0 z-50 grid place-items-center bg-[rgba(0,0,0,0.62)] p-5"
      role="presentation"
      onMouseDown={(event) => {
        if (event.target === event.currentTarget) onClose();
      }}
    >
      <div
        className="relative grid w-[min(780px,100%)] grid-cols-[0.9fr_1.1fr] overflow-hidden rounded-md border border-border bg-surface shadow-[0_26px_70px_rgba(0,0,0,0.42)] max-[760px]:max-h-[calc(100vh-40px)] max-[760px]:grid-cols-1 max-[760px]:overflow-y-auto"
        role="dialog"
        aria-modal="true"
        aria-labelledby="unit-dialog-title"
      >
        <button
          className="absolute right-[15px] top-[15px] z-[2] grid h-[38px] w-[38px] place-items-center rounded-sm border border-border bg-surface text-ink"
          onClick={onClose}
          aria-label="Đóng"
        >
          <X size={19} />
        </button>
        <div
          className={`relative min-h-[430px] overflow-hidden max-[760px]:min-h-[245px] ${ACCENT_BACKGROUND[unit.accent]}`}
        >
          <img
            className="block h-full min-h-[430px] w-full object-cover text-[12px] leading-[1.4] text-[rgba(232,242,239,0.68)] max-[760px]:h-[245px] max-[760px]:min-h-[245px]"
            src={unit.image}
            alt={`Hình ảnh tại kho ${unit.name}`}
          />
        </div>
        <div className="p-[55px_37px_35px] max-[760px]:p-[30px_24px_25px]">
          <p className="m-0 mb-[18px] font-mono text-mono uppercase tracking-[0.08em] text-brand">
            {unit.name}
          </p>
          <h2
            id="unit-dialog-title"
            className="m-0 mb-[19px] text-[34px] font-bold leading-[1.08] tracking-[-0.04em] text-ink max-[760px]:text-[29px]"
          >
            Phương án lưu trữ
            <br />
            cho {unit.idealFor.toLowerCase()}.
          </h2>
          <p className="m-0 mb-[25px] text-[13px] leading-[1.65] text-muted">{unit.description}</p>
          <div className="mb-7 grid grid-cols-3 gap-[10px] border-y border-border py-[15px]">
            <span className="grid gap-[3px]">
              <small className="text-[9px] text-muted">Quy mô</small>
              <strong className="text-[11px] font-bold text-ink">{unit.capacity}</strong>
            </span>
            <span className="grid gap-[3px]">
              <small className="text-[9px] text-muted">Thông số</small>
              <strong className="text-[11px] font-bold text-ink">{unit.size}</strong>
            </span>
            <span className="grid gap-[3px]">
              <small className="text-[9px] text-muted">Từ</small>
              <strong className="text-[11px] font-bold text-ink">
                {formatPrice(unit.monthlyPrice)}đ/tháng
              </strong>
            </span>
          </div>
          <button
            className="inline-flex min-h-11 w-full items-center justify-center gap-2.5 rounded-sm border border-transparent bg-brand px-[18px] py-0 text-[14px] font-[750] text-surface transition-colors duration-[180ms] ease hover:bg-brand-strong"
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
