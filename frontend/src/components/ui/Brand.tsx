import { Warehouse } from "lucide-react";

type BrandProps = { className?: string; onClick: () => void };

export function Brand({ className = "", onClick }: BrandProps) {
  return (
    <button className={`brand ${className}`} onClick={onClick} aria-label="Về trang chủ Kho Mộc">
      <span className="brand-mark">
        <Warehouse size={18} strokeWidth={2.25} />
      </span>
      <span>
        kho<span className="brand-dot">.</span>mộc
      </span>
    </button>
  );
}
