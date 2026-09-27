import { ArrowRight } from "lucide-react";
import { formatPrice, UnitType } from "../../mocks/catalog";
import { Navigate } from "../../app/types";

type UnitCardProps = { unit: UnitType; navigate: Navigate; onDetails?: () => void };

export function UnitCard({ unit, navigate, onDetails }: UnitCardProps) {
  return (
    <article className={`unit-card accent-${unit.accent}`}>
      <div className="unit-card-visual">
        <img className="unit-card-image" src={unit.image} alt={`Hình ảnh tại kho ${unit.name}`} />
      </div>
      <div className="unit-card-body">
        <div className="unit-card-title">
          <div>
            <h3>{unit.name}</h3>
            <p>{unit.size}</p>
          </div>
          <span className="price">
            <strong>{formatPrice(unit.monthlyPrice)}đ</strong>
            <small>/ tháng</small>
          </span>
        </div>
        <p className="unit-card-description">{unit.description}</p>
        <div className="unit-card-footer">
          <span>{unit.idealFor}</span>
          <button
            className="card-link"
            onClick={onDetails ?? (() => navigate("/rental-requests/new"))}
          >
            {onDetails ? "Xem chi tiết" : "Chọn loại này"} <ArrowRight size={15} />
          </button>
        </div>
      </div>
    </article>
  );
}
