import { NoticeTone } from "../../app/types";

export function DemoNotice({ tone, children }: { tone: NoticeTone; children: React.ReactNode }) {
  return (
    <div className={`demo-notice notice-${tone}`} role={tone === "error" ? "alert" : "status"}>
      <span className="notice-dot" />
      {children}
    </div>
  );
}
