import { AlertTriangle, Check, UserRound, X } from "lucide-react";
import { useState } from "react";
import { formatPrice } from "../../mocks/catalog";
import {
  extendRequests as initialRequests,
  type ExtendRequest,
  type ExtendRequestStatus,
} from "../../mocks/fm";
import { Button } from "../../components/ui/Button";
import { DemoNotice } from "../../components/ui/DemoNotice";
import { DetailPanel } from "../../components/ui/DetailPanel";
import { SurfaceState } from "../../components/ui/SurfaceState";

type StatusFilter = "all" | ExtendRequestStatus;

const STATUS_LABEL: Record<ExtendRequestStatus, string> = {
  PendingApproval: "Chờ duyệt",
  ApprovedPendingPayment: "Đã duyệt · chờ thanh toán",
  Rejected: "Đã từ chối",
};

const STATUS_BADGE: Record<ExtendRequestStatus, string> = {
  PendingApproval: "bg-warning/14 text-warning",
  ApprovedPendingPayment: "bg-success/14 text-success",
  Rejected: "bg-danger/14 text-danger",
};

const FILTER_SELECT =
  "min-w-[220px] rounded-sm border border-border bg-surface px-[11px] py-[10px] pr-[30px] text-[12px] font-semibold text-ink";

function estimatedInvoice(request: ExtendRequest) {
  return request.extraMonths * request.monthlyPrice;
}

export function ExtendRequestQueuePage() {
  const [statusFilter, setStatusFilter] = useState<StatusFilter>("PendingApproval");
  const [items, setItems] = useState<ExtendRequest[]>(initialRequests);
  const [openId, setOpenId] = useState<string | null>(null);
  const [mode, setMode] = useState<"view" | "reject">("view");
  const [rejectReason, setRejectReason] = useState("");
  const [busyId, setBusyId] = useState<string | null>(null);

  const list = items.filter((request) => statusFilter === "all" || request.status === statusFilter);
  const pendingCount = items.filter((request) => request.status === "PendingApproval").length;
  const openRequest = items.find((request) => request.id === openId) ?? null;

  function openDetails(request: ExtendRequest) {
    setOpenId(request.id);
    setMode("view");
    setRejectReason("");
  }

  function approve(request: ExtendRequest) {
    setBusyId(request.id);
    window.setTimeout(() => {
      setBusyId(null);
      setItems((current) =>
        current.map((item) =>
          item.id === request.id ? { ...item, status: "ApprovedPendingPayment" } : item,
        ),
      );
      setOpenId(null);
    }, 800);
  }

  function reject(request: ExtendRequest) {
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

  return (
    <main>
      <div className="mb-8">
        <h1 className="m-0 text-[28px] tracking-[-0.03em] text-ink">Hàng chờ yêu cầu gia hạn</h1>
        <p className="mt-2 text-[13px] text-muted">
          Yêu cầu gia hạn hợp đồng khách gửi — duyệt để tạo hoá đơn gia hạn, hoặc từ chối.
        </p>
      </div>

      <div className="mb-6 flex flex-wrap items-center gap-3">
        <select
          className={FILTER_SELECT}
          value={statusFilter}
          onChange={(event) => setStatusFilter(event.target.value as StatusFilter)}
        >
          <option value="all">Tất cả trạng thái</option>
          <option value="PendingApproval">
            Chờ duyệt {pendingCount > 0 && `(${pendingCount})`}
          </option>
          <option value="ApprovedPendingPayment">Đã duyệt · chờ thanh toán</option>
          <option value="Rejected">Đã từ chối</option>
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
                <p className="m-0 flex items-center gap-2 text-[15px] font-bold text-ink">
                  <UserRound size={15} /> {request.customerName}
                </p>
                <p className="mb-0 mt-1.5 text-[13px] text-muted">
                  Khoang {request.unitCode} · Gia hạn {request.extraMonths} tháng · Hết hạn hiện tại{" "}
                  {request.currentEndDate}
                </p>
                <p className="mb-0 mt-1 font-mono text-[10px] uppercase tracking-[0.06em] text-muted">
                  Gửi lúc {request.requestedAt}
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
          eyebrow={`Gia hạn · Khoang ${openRequest.unitCode}`}
          onClose={() => setOpenId(null)}
        >
          <div className="grid gap-2.5 text-[13px] text-muted">
            <span>Hết hạn hiện tại: {openRequest.currentEndDate}</span>
            <span>Gia hạn thêm: {openRequest.extraMonths} tháng</span>
            <span>Giá thuê hiện tại: {formatPrice(openRequest.monthlyPrice)}đ / tháng</span>
          </div>

          <div className="mt-4 grid gap-2 border border-border bg-surface-subtle p-3.5 text-[12px]">
            <div className="flex items-center justify-between">
              <span className="text-muted">Hoá đơn gia hạn dự kiến</span>
              <strong>{formatPrice(estimatedInvoice(openRequest))}đ</strong>
            </div>
          </div>

          {openRequest.status === "PendingApproval" && mode === "view" && (
            <div className="mt-6 flex items-center gap-3">
              <Button pending={busyId === openRequest.id} onClick={() => approve(openRequest)}>
                {busyId !== openRequest.id && <Check size={16} />}
                Duyệt gia hạn
              </Button>
              <Button variant="secondary" onClick={() => setMode("reject")}>
                <X size={16} /> Từ chối
              </Button>
            </div>
          )}

          {openRequest.status === "PendingApproval" && mode === "reject" && (
            <div className="mt-6 border border-border bg-surface-subtle p-4">
              <label className="grid gap-1.5 text-[12px] font-bold text-ink">
                Lý do từ chối
                <textarea
                  className="min-h-[80px] rounded-sm border border-border bg-surface p-2.5 text-[13px] font-normal text-ink"
                  value={rejectReason}
                  onChange={(event) => setRejectReason(event.target.value)}
                  placeholder="Cho khách biết vì sao yêu cầu chưa được duyệt…"
                />
              </label>
              <div className="mt-3 flex items-center gap-2.5">
                <Button disabled={!rejectReason.trim()} onClick={() => reject(openRequest)}>
                  <AlertTriangle size={16} /> Xác nhận từ chối
                </Button>
                <button
                  className="border-0 bg-transparent px-3 py-2 text-[12px] text-muted hover:text-brand"
                  onClick={() => setMode("view")}
                >
                  Quay lại
                </button>
              </div>
            </div>
          )}

          {openRequest.status === "ApprovedPendingPayment" && (
            <div className="mt-6">
              <DemoNotice tone="success">
                Đã duyệt — hệ thống tạo hoá đơn gia hạn, chờ khách thanh toán ở trang Hoá đơn.
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
