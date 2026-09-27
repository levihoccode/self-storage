import { Check, Loader2, UserRound } from "lucide-react";
import { useState } from "react";
import {
  fsStaff,
  recordReturnAppointment,
  returnRequests as initialRequests,
  type ReturnRequest,
  type ReturnRequestStatus,
} from "../../mocks/fm";
import { DemoNotice } from "../../components/ui/DemoNotice";
import { DetailPanel } from "../../components/ui/DetailPanel";
import { SurfaceState } from "../../components/ui/SurfaceState";

const TAB_LABEL: Record<ReturnRequestStatus, string> = {
  Pending: "Chờ phân công",
  Assigned: "Đã phân công",
};

const PRIMARY_BUTTON =
  "inline-flex min-h-11 items-center justify-center gap-2.5 rounded-sm border border-transparent bg-brand px-[18px] text-[14px] font-[750] text-background transition-colors duration-[180ms] ease hover:bg-brand-strong disabled:cursor-not-allowed disabled:opacity-60";
const TAB_BUTTON =
  "min-h-10 rounded-sm border border-border bg-surface px-4 text-[13px] font-bold text-muted transition-colors";
const TAB_BUTTON_ACTIVE = "border-brand bg-brand-soft text-brand";

function staffName(staffId: string | null) {
  return fsStaff.find((staff) => staff.id === staffId)?.name ?? null;
}

export function ReturnRequestQueuePage() {
  const [items, setItems] = useState<ReturnRequest[]>(initialRequests);
  const [tab, setTab] = useState<ReturnRequestStatus>("Pending");
  const [openId, setOpenId] = useState<string | null>(null);
  const [selectedStaffId, setSelectedStaffId] = useState("");
  const [busyId, setBusyId] = useState<string | null>(null);

  const list = items.filter((request) => request.status === tab);
  const pendingCount = items.filter((request) => request.status === "Pending").length;
  const openRequest = items.find((request) => request.id === openId) ?? null;

  function openDetails(request: ReturnRequest) {
    setOpenId(request.id);
    setSelectedStaffId("");
  }

  function assign(request: ReturnRequest) {
    if (!selectedStaffId) return;
    setBusyId(request.id);
    window.setTimeout(() => {
      setBusyId(null);
      setItems((current) =>
        current.map((item) =>
          item.id === request.id ? { ...item, status: "Assigned", staffId: selectedStaffId } : item,
        ),
      );
      recordReturnAppointment(request, selectedStaffId);
      setOpenId(null);
    }, 700);
  }

  return (
    <main>
      <div className="mb-8">
        <h1 className="m-0 text-[28px] tracking-[-0.03em] text-ink">Hàng chờ yêu cầu trả kho</h1>
        <p className="mt-2 text-[13px] text-muted">
          Yêu cầu trả kho khách gửi từ trang chi tiết hợp đồng — phân công FS phụ trách.
        </p>
      </div>

      <div className="mb-6">
        <DemoNotice tone="pending">
          Sau khi phân công, hệ thống tự tạo lịch hẹn trả kho ở trang{" "}
          <strong>Lịch hẹn &amp; phân công FS</strong> — không cần thao tác gì thêm ở đó.
        </DemoNotice>
      </div>

      <div className="mb-6 flex items-center gap-2.5">
        {(["Pending", "Assigned"] as const).map((status) => (
          <button
            key={status}
            className={`${TAB_BUTTON} ${tab === status ? TAB_BUTTON_ACTIVE : ""}`}
            onClick={() => setTab(status)}
          >
            {TAB_LABEL[status]}
            {status === "Pending" && pendingCount > 0 && (
              <span className="ml-2 rounded-full bg-warning/14 px-2 py-0.5 text-[11px] text-warning">
                {pendingCount}
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
                <p className="m-0 flex items-center gap-2 text-[15px] font-bold text-ink">
                  <UserRound size={15} /> {request.customerName}
                </p>
                <p className="mb-0 mt-1.5 text-[13px] text-muted">
                  Khoang {request.unitCode} · Mong muốn trả ngày {request.preferredDate}
                </p>
              </div>
              {request.status === "Assigned" ? (
                <span className="whitespace-nowrap rounded-full bg-success/14 px-[9px] py-[6px] text-[11px] font-extrabold text-success">
                  FS: {staffName(request.staffId)}
                </span>
              ) : (
                <span className="whitespace-nowrap rounded-full bg-warning/14 px-[9px] py-[6px] text-[11px] font-extrabold text-warning">
                  Chờ phân công
                </span>
              )}
            </button>
          ))}
        </div>
      )}

      {openRequest && (
        <DetailPanel
          title={openRequest.customerName}
          eyebrow={`Trả kho · ${openRequest.unitCode}`}
          onClose={() => setOpenId(null)}
        >
          <div className="grid gap-2.5 text-[13px] text-muted">
            <span>Mong muốn trả ngày {openRequest.preferredDate}</span>
          </div>
          {openRequest.reason && (
            <p className="mb-0 mt-4 text-[13px] leading-[1.6] text-ink">{openRequest.reason}</p>
          )}

          {openRequest.status === "Assigned" ? (
            <div className="mt-6">
              <DemoNotice tone="success">
                Đã phân công {staffName(openRequest.staffId)} phụ trách — lịch hẹn trả kho đã được
                tạo tự động.
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
                <button
                  className={PRIMARY_BUTTON}
                  disabled={!selectedStaffId || busyId === openRequest.id}
                  onClick={() => assign(openRequest)}
                >
                  {busyId === openRequest.id ? (
                    <Loader2
                      className="animate-[surface-state-spin_0.9s_linear_infinite]"
                      size={16}
                    />
                  ) : (
                    <Check size={16} />
                  )}
                  Phân công
                </button>
              </div>
            </>
          )}
        </DetailPanel>
      )}
    </main>
  );
}
