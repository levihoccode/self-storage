import { AlertTriangle, Check, Loader2, Mail, Phone, X } from "lucide-react";
import { useState } from "react";
import { unitTypes } from "../../mocks/catalog";
import { fmUnits, rentalRequests as initialRequests, type RentalRequest } from "../../mocks/fm";
import { DemoNotice } from "../../components/ui/DemoNotice";
import { DetailPanel } from "../../components/ui/DetailPanel";
import { SurfaceState } from "../../components/ui/SurfaceState";

type StatusFilter = "all" | RentalRequest["status"];

const STATUS_LABEL: Record<RentalRequest["status"], string> = {
  Pending: "Chờ xử lý",
  Approved: "Đã duyệt",
  Rejected: "Đã từ chối",
  Expired: "Hết hạn",
  Wishlisted: "Danh sách chờ",
};

const STATUS_BADGE: Record<RentalRequest["status"], string> = {
  Pending: "bg-warning/14 text-warning",
  Approved: "bg-success/14 text-success",
  Rejected: "bg-danger/14 text-danger",
  Expired: "bg-surface-subtle text-muted",
  Wishlisted: "bg-brand-soft text-brand",
};

const PRIMARY_BUTTON =
  "inline-flex min-h-11 items-center justify-center gap-2.5 rounded-sm border border-transparent bg-brand px-[18px] text-[14px] font-[750] text-background transition-colors duration-[180ms] ease hover:bg-brand-strong disabled:cursor-not-allowed disabled:opacity-60";
const SECONDARY_BUTTON =
  "inline-flex min-h-11 items-center justify-center gap-2.5 rounded-sm border border-brand bg-transparent px-[18px] text-[14px] font-[750] text-brand transition-colors duration-[180ms] ease hover:bg-brand hover:text-background disabled:cursor-not-allowed disabled:opacity-60";
const FILTER_SELECT =
  "min-w-[170px] rounded-sm border border-border bg-surface px-[11px] py-[10px] pr-[30px] text-[12px] font-semibold text-ink";

function unitTypeName(unitTypeId: string) {
  return unitTypes.find((type) => type.id === unitTypeId)?.name ?? unitTypeId;
}

export function RentalRequestQueuePage() {
  const [statusFilter, setStatusFilter] = useState<StatusFilter>("Pending");
  const [items, setItems] = useState<RentalRequest[]>(initialRequests);
  const [openId, setOpenId] = useState<string | null>(null);
  const [mode, setMode] = useState<"view" | "reject">("view");
  const [selectedUnitId, setSelectedUnitId] = useState("");
  const [rejectReason, setRejectReason] = useState("");
  const [busyId, setBusyId] = useState<string | null>(null);
  const [conflictId, setConflictId] = useState<string | null>(null);

  const list = items.filter((request) => statusFilter === "all" || request.status === statusFilter);
  const openRequest = items.find((request) => request.id === openId) ?? null;

  function openDetails(request: RentalRequest) {
    setOpenId(request.id);
    setMode("view");
    setSelectedUnitId("");
    setRejectReason("");
    setConflictId(null);
  }

  function approve(request: RentalRequest) {
    if (!selectedUnitId) return;
    setBusyId(request.id);
    window.setTimeout(() => {
      setBusyId(null);
      if (request.simulateConflict) {
        setConflictId(request.id);
        return;
      }
      const unit = fmUnits.find((item) => item.id === selectedUnitId);
      setItems((current) =>
        current.map((item) =>
          item.id === request.id
            ? { ...item, status: "Approved", approvedUnitCode: unit?.code }
            : item,
        ),
      );
      setOpenId(null);
    }, 800);
  }

  function reject(request: RentalRequest) {
    if (!rejectReason.trim()) return;
    setItems((current) =>
      current.map((item) =>
        item.id === request.id
          ? { ...item, status: "Rejected", rejectReason: rejectReason.trim() }
          : item,
      ),
    );
    setOpenId(null);
  }

  const availableUnitsForRequest = (request: RentalRequest) =>
    fmUnits.filter((unit) => unit.unitTypeId === request.unitTypeId && unit.status === "Available");

  return (
    <main>
      <div className="mb-8">
        <h1 className="m-0 text-[28px] tracking-[-0.03em] text-ink">Hàng chờ yêu cầu đặt kho</h1>
        <p className="mt-2 text-[13px] text-muted">
          Yêu cầu gửi tới chi nhánh của bạn — gán khoang phù hợp hoặc từ chối.
        </p>
      </div>

      <div className="mb-6">
        <select
          className={FILTER_SELECT}
          value={statusFilter}
          onChange={(event) => setStatusFilter(event.target.value as StatusFilter)}
        >
          <option value="all">Tất cả trạng thái</option>
          <option value="Pending">Chờ xử lý</option>
          <option value="Approved">Đã duyệt</option>
          <option value="Rejected">Đã từ chối</option>
          <option value="Expired">Hết hạn</option>
          <option value="Wishlisted">Danh sách chờ</option>
        </select>
      </div>

      {list.length === 0 ? (
        <SurfaceState variant="empty" title="Không có yêu cầu nào phù hợp bộ lọc" />
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
                  <p className="m-0 text-[15px] font-bold text-ink">{request.customerName}</p>
                  {!request.hasAccount && (
                    <span className="rounded-full bg-warning/14 px-2 py-0.5 text-[10px] font-bold text-warning">
                      Chưa có tài khoản
                    </span>
                  )}
                </div>
                <p className="mb-0 mt-1.5 text-[13px] text-muted">
                  {unitTypeName(request.unitTypeId)} · {request.desiredSize}
                </p>
                <p className="mb-0 mt-1 font-mono text-[10px] uppercase tracking-[0.06em] text-muted">
                  Gửi lúc {request.createdAt}
                </p>
              </div>
              <span
                className={`whitespace-nowrap rounded-full px-[9px] py-[6px] text-[11px] font-extrabold ${STATUS_BADGE[request.status]}`}
              >
                {STATUS_LABEL[request.status]}
              </span>
            </button>
          ))}
        </div>
      )}

      {openRequest && (
        <DetailPanel
          title={openRequest.customerName}
          eyebrow={`Yêu cầu · ${unitTypeName(openRequest.unitTypeId)}`}
          onClose={() => setOpenId(null)}
        >
          <div className="grid gap-2.5 text-[13px] text-muted">
            <span className="flex items-center gap-2">
              <Phone size={14} /> {openRequest.customerPhone}
            </span>
            <span className="flex items-center gap-2">
              <Mail size={14} /> {openRequest.customerEmail}
            </span>
            {!openRequest.hasAccount && (
              <span className="w-fit rounded-full bg-warning/14 px-2 py-0.5 text-[10px] font-bold text-warning">
                Chưa có tài khoản — khách sẽ nhận email mời đăng ký
              </span>
            )}
          </div>
          <p className="mb-0 mt-4 text-[13px] leading-[1.6] text-ink">{openRequest.note}</p>

          {conflictId === openRequest.id && (
            <div className="mt-5">
              <DemoNotice tone="error">
                Khoang đã không còn khả dụng, vui lòng chọn khoang khác.
              </DemoNotice>
            </div>
          )}

          {openRequest.status === "Pending" && mode === "view" && (
            <>
              <label className="mt-6 grid gap-1.5 text-[12px] font-bold text-ink">
                Chọn khoang phù hợp
                <select
                  className="rounded-sm border border-border bg-surface-subtle px-3 py-2.5 text-[13px] text-ink"
                  value={selectedUnitId}
                  onChange={(event) => setSelectedUnitId(event.target.value)}
                >
                  <option value="">— Chọn khoang —</option>
                  {availableUnitsForRequest(openRequest).map((unit) => (
                    <option key={unit.id} value={unit.id}>
                      {unit.code} · {unit.size}
                    </option>
                  ))}
                </select>
              </label>
              {availableUnitsForRequest(openRequest).length === 0 && (
                <p className="mb-0 mt-2 text-[12px] text-danger">
                  Hiện không còn khoang trống đúng loại yêu cầu.
                </p>
              )}
              <div className="mt-5 flex items-center gap-3">
                <button
                  className={PRIMARY_BUTTON}
                  disabled={!selectedUnitId || busyId === openRequest.id}
                  onClick={() => approve(openRequest)}
                >
                  {busyId === openRequest.id ? (
                    <Loader2
                      className="animate-[surface-state-spin_0.9s_linear_infinite]"
                      size={16}
                    />
                  ) : (
                    <Check size={16} />
                  )}
                  Duyệt &amp; gán khoang
                </button>
                <button className={SECONDARY_BUTTON} onClick={() => setMode("reject")}>
                  <X size={16} /> Từ chối
                </button>
              </div>
            </>
          )}

          {openRequest.status === "Pending" && mode === "reject" && (
            <div className="mt-6 border border-border bg-surface-subtle p-4">
              <label className="grid gap-1.5 text-[12px] font-bold text-ink">
                Lý do từ chối
                <textarea
                  className="min-h-[80px] rounded-sm border border-border bg-surface p-2.5 text-[13px] font-normal text-ink"
                  value={rejectReason}
                  onChange={(event) => setRejectReason(event.target.value)}
                  placeholder="Cho khách biết vì sao yêu cầu chưa phù hợp…"
                />
              </label>
              <div className="mt-3 flex items-center gap-2.5">
                <button
                  className={PRIMARY_BUTTON}
                  disabled={!rejectReason.trim()}
                  onClick={() => reject(openRequest)}
                >
                  <AlertTriangle size={16} /> Xác nhận từ chối
                </button>
                <button
                  className="border-0 bg-transparent px-3 py-2 text-[12px] text-muted hover:text-brand"
                  onClick={() => setMode("view")}
                >
                  Quay lại
                </button>
              </div>
            </div>
          )}

          {openRequest.status === "Approved" && (
            <div className="mt-6">
              <DemoNotice tone="success">
                Đã gán khoang {openRequest.approvedUnitCode} cho yêu cầu này.
              </DemoNotice>
            </div>
          )}
          {openRequest.status === "Rejected" && (
            <div className="mt-6">
              <DemoNotice tone="error">{openRequest.rejectReason}</DemoNotice>
            </div>
          )}
        </DetailPanel>
      )}
    </main>
  );
}
