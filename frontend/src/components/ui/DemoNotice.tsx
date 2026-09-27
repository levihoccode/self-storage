import { Loader2 } from "lucide-react";
import { NoticeTone } from "../../app/types";

const TONE_CLASSES: Record<NoticeTone, string> = {
  info: "text-info bg-[rgba(37,99,235,0.13)] border-[rgba(157,197,255,0.25)]",
  success: "text-success bg-[rgba(21,128,61,0.13)] border-[rgba(140,221,166,0.25)]",
  error: "text-danger bg-[rgba(185,28,28,0.14)] border-[rgba(255,155,148,0.25)]",
  pending: "text-muted bg-surface-subtle border-border",
};

export function DemoNotice({ tone, children }: { tone: NoticeTone; children: React.ReactNode }) {
  return (
    <div
      className={`mt-[19px] flex items-start gap-2 rounded-sm border px-3 py-[11px] text-[11px] leading-normal ${TONE_CLASSES[tone]}`}
      role={tone === "error" ? "alert" : "status"}
    >
      {tone === "pending" ? (
        <Loader2
          className="mt-[2px] shrink-0 animate-[surface-state-spin_0.9s_linear_infinite]"
          size={12}
        />
      ) : (
        <span className="mt-[5px] h-[7px] w-[7px] shrink-0 rounded-full bg-current" />
      )}
      {children}
    </div>
  );
}
