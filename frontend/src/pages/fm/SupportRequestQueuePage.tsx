import { Check, UserRound } from "lucide-react";
import { useState } from "react";
import {
  fsStaff,
  supportRequests as initialRequests,
  type SupportIssueType,
  type SupportRequest,
  type SupportRequestStatus,
} from "../../mocks/fm";
import { Button } from "../../components/ui/Button";
import { DemoNotice } from "../../components/ui/DemoNotice";
import { DetailPanel } from "../../components/ui/DetailPanel";
import { SurfaceState } from "../../components/ui/SurfaceState";

const TAB_LABEL: Record<SupportRequestStatus, string> = {
  Open: "Đang mở",
  Assigned: "Đã phân công",
};

const ISSUE_LABEL: Record<SupportIssueType, string> = {
  LostKey: "Mất chìa khoá",
  AccessCode: "Lỗi mã ra vào",
  UnitDamage: "Hư hỏng khoang",
  Other: "Khác",
};

const ISSUE_BADGE: Record<SupportIssueType, string> = {
  LostKey: "bg-warning/14 text-warning",
  AccessCode: "bg-info/14 text-info",
  UnitDamage: "bg-danger/14 text-danger",
  Other: "bg-surface-subtle text-muted",
};

const TAB_BUTTON =
  "min-h-10 rounded-sm border border-border bg-surface px-4 text-[13px] font-bold text-muted transition-colors";
const TAB_BUTTON_ACTIVE = "border-brand bg-brand-soft text-brand";

function staffName(staffId: string | null) {
  return fsStaff.find((staff) => staff.id === staffId)?.name ?? null;
}

export function SupportRequestQueuePage() {
  const [items, setItems] = useState<SupportRequest[]>(initialRequests);
  const [tab, setTab] = useState<SupportRequestStatus>("Open");
  const [openId, setOpenId] = useState<string | null>(null);
  const [selectedStaffId, setSelectedStaffId] = useState("");
  const [busyId, setBusyId] = useState<string | null>(null);

  const list = items.filter((request) => request.status === tab);
  const openCount = items.filter((request) => request.status === "Open").length;
  const openRequest = items.find((request) => request.id === openId) ?? null;

  function openDetails(request: SupportRequest) {
    setOpenId(request.id);
    setSelectedStaffId("");
  }

  function assign(request: SupportRequest) {
    if (!selectedStaffId) return;
    setBusyId(request.id);
    window.setTimeout(() => {
      setBusyId(null);
      setItems((current) =>
        current.map((item) =>
          item.id === request.id ? { ...item, status: "Assigned", staffId: selectedStaffId } : item,
        ),
      );
      setOpenId(null);
    }, 700);
  }

  return (
    <main>
      <div className="mb-8">
        <h1 className="m-0 text-[28px] tracking-[-0.03em] text-ink">Hàng chờ yêu cầu hỗ trợ</h1>
        <p className="mt-2 text-[13px] text-muted">
          Sự cố khách báo hoặc FS tự ghi nhận tại kho — phân công FS xử lý.
        </p>
      </div>

      <div className="mb-6 flex items-center gap-2.5">
        {(["Open", "Assigned"] as const).map((status) => (
          <button
            key={status}
            className={`${TAB_BUTTON} ${tab === status ? TAB_BUTTON_ACTIVE : ""}`}
            onClick={() => setTab(status)}
          >
            {TAB_LABEL[status]}
            {status === "Open" && openCount > 0 && (
              <span className="ml-2 rounded-full bg-warning/14 px-2 py-0.5 text-[11px] text-warning">
                {openCount}
              </span>
            )}
          </button>
        ))}
      </div>

      {list.length === 0 ? (
        <SurfaceState variant="empty" title="Không có yêu cầu nào trong mục này" />
      ) : (
        <div className="grid gap-3">
          {list.map((request) => (
            <button
              key={request.id}
              className="flex items-start justify-between gap-5 border border-border bg-surface p-5 text-left transition-colors hover:border-brand max-[760px]:flex-col"
              onClick={() => openDetails(request)}
            >
              <div>
                <div className="flex items-center gap-2.5">
                  <span
                    className={`rounded-full px-2 py-0.5 text-[10px] font-bold ${ISSUE_BADGE[request.issueType]}`}
                  >
                    {ISSUE_LABEL[request.issueType]}
                  </span>
                  <p className="m-0 text-[15px] font-bold text-ink">Khoang {request.unitCode}</p>
                </div>
                <p className="mb-0 mt-1.5 flex items-center gap-1.5 text-[13px] text-muted">
                  <UserRound size={13} /> {request.reporterName}{" "}
                  <span className="font-mono text-[10px] uppercase text-muted">
                    ({request.reporterRole === "Customer" ? "Khách" : "FS"})
                  </span>
                </p>
              </div>
              {request.status === "Assigned" ? (
                <span className="whitespace-nowrap rounded-full bg-success/14 px-[9px] py-[6px] text-[11px] font-extrabold text-success">
                  FS: {staffName(request.staffId)}
                </span>
              ) : (
                <span className="whitespace-nowrap rounded-full bg-warning/14 px-[9px] py-[6px] text-[11px] font-extrabold text-warning">
                  Chưa phân công
                </span>
              )}
            </button>
          ))}
        </div>
      )}

      {openRequest && (
        <DetailPanel
          title={`Khoang ${openRequest.unitCode}`}
          eyebrow={ISSUE_LABEL[openRequest.issueType]}
          onClose={() => setOpenId(null)}
        >
          <div className="grid gap-2.5 text-[13px] text-muted">
            <span>
              Người báo: {openRequest.reporterName} (
              {openRequest.reporterRole === "Customer" ? "Khách thuê" : "Nhân viên FS"})
            </span>
            <span>
              Hợp đồng liên quan: {openRequest.contractId ?? "Không có hợp đồng liên quan"}
            </span>
          </div>
          <p className="mb-0 mt-4 text-[13px] leading-[1.6] text-ink">{openRequest.description}</p>

          {openRequest.status === "Assigned" ? (
            <div className="mt-6">
              <DemoNotice tone="success">
                Đã phân công {staffName(openRequest.staffId)} xử lý sự cố này.
              </DemoNotice>
            </div>
          ) : (
            <>
              <label className="mt-6 grid gap-1.5 text-[12px] font-bold text-ink">
                Chọn nhân viên FS
                <select
                  className="rounded-sm border border-border bg-surface-subtle px-3 py-2.5 text-[13px] text-ink"
                  value={selectedStaffId}
                  onChange={(event) => setSelectedStaffId(event.target.value)}
                >
                  <option value="">— Chọn nhân viên —</option>
                  {fsStaff.map((staff) => (
                    <option key={staff.id} value={staff.id}>
                      {staff.name} · {staff.phone}
                    </option>
                  ))}
                </select>
              </label>
              <div className="mt-5">
                <Button
                  pending={busyId === openRequest.id}
                  disabled={!selectedStaffId}
                  onClick={() => assign(openRequest)}
                >
                  {busyId !== openRequest.id && <Check size={16} />}
                  Phân công
                </Button>
              </div>
            </>
          )}
        </DetailPanel>
      )}
    </main>
  );
}
