import { Loader2 } from "lucide-react";
import { NoticeTone } from "../../app/types";

export function DemoNotice({ tone, children }: { tone: NoticeTone; children: React.ReactNode }) {
  return (
    <div className={`demo-notice notice-${tone}`} role={tone === "error" ? "alert" : "status"}>
      {tone === "pending" ? (
        <Loader2 className="surface-state-spin notice-dot-spin" size={12} />
      ) : (
        <span className="notice-dot" />
      )}
      {children}
    </div>
  );
}
