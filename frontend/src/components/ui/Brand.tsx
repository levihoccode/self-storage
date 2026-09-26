import { Warehouse } from "lucide-react";

type BrandProps = {
  className?: string;
  dotClassName?: string;
  /** Public header shrinks the mark/text once the page has scrolled. */
  compact?: boolean;
  onClick: () => void;
};

export function Brand({
  className = "",
  dotClassName = "text-accent",
  compact = false,
  onClick,
}: BrandProps) {
  return (
    <button
      className={`inline-flex items-center gap-2.5 border-0 bg-transparent p-0 font-extrabold tracking-[0.01em] text-ink ${compact ? "text-[17px]" : "text-[19px]"} ${className}`}
      onClick={onClick}
      aria-label="Về trang chủ Kho Mộc"
    >
      <span
        className={`inline-grid place-items-center rounded-[4px] bg-brand tracking-normal text-white ${compact ? "h-7 w-7" : "h-[31px] w-[31px]"}`}
      >
        <Warehouse size={18} strokeWidth={2.25} />
      </span>
      <span>
        kho<span className={dotClassName}>.</span>mộc
      </span>
    </button>
  );
}
