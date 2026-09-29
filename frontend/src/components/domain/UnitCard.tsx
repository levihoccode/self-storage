import { ArrowRight } from "lucide-react";
import { formatPrice, UnitType } from "../../mocks/catalog";
import { Navigate } from "../../app/types";

type UnitCardProps = { unit: UnitType; navigate: Navigate; onDetails?: () => void };

const ACCENT_BACKGROUND: Record<UnitType["accent"], string> = {
  sand: "bg-unit-sand",
  teal: "bg-unit-teal",
  ink: "bg-unit-ink",
};

export function UnitCard({ unit, navigate, onDetails }: UnitCardProps) {
  return (
    <article className="overflow-hidden rounded-md border border-border bg-surface transition-[border-color] duration-[180ms] ease hover:border-brand">
      <div
        className={`relative h-[218px] overflow-hidden border-b border-border ${ACCENT_BACKGROUND[unit.accent]}`}
      >
        <img
          className="block h-full w-full object-cover text-[12px] leading-[1.4] text-[rgba(232,242,239,0.68)]"
          src={unit.image}
          alt={`Hình ảnh tại kho ${unit.name}`}
        />
      </div>
      <div className="p-[23px]">
        <div className="flex items-start justify-between gap-4">
          <div>
            <h3 className="m-0 mb-1 text-[20px] font-bold tracking-[-0.025em] text-ink">
              {unit.name}
            </h3>
            <p className="m-0 text-[11px] text-muted">{unit.size}</p>
          </div>
          <span className="flex flex-col items-end text-brand">
            <strong className="text-[14px] font-bold">{formatPrice(unit.monthlyPrice)}đ</strong>
            <small className="text-[10px] font-medium text-muted">/ tháng</small>
          </span>
        </div>
        <p className="m-0 mb-[22px] mt-[19px] min-h-[54px] text-[13px] leading-[1.6] text-muted">
          {unit.description}
        </p>
        <div className="flex items-center justify-between gap-3 border-t border-border pt-[15px] text-[11px] text-muted">
          <span>{unit.idealFor}</span>
          <button
            className="inline-flex items-center gap-[5px] border-0 bg-transparent p-0 text-[12px] font-extrabold text-brand hover:text-brand-strong"
            onClick={onDetails ?? (() => navigate("/rental-requests/new"))}
          >
            {onDetails ? "Xem chi tiết" : "Chọn loại này"} <ArrowRight size={15} />
          </button>
        </div>
      </div>
    </article>
  );
}
