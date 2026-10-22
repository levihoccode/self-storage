import { AlertTriangle, ArrowRight, Check, Mail, Phone, X } from "lucide-react";
import { useEffect, useState } from "react";
import { ApiError } from "../../app/api";
import {
  approveRentalRequest,
  currentFmFacilityId,
  fetchRentalRequests,
  fetchUnitTypes,
  formatDate,
  formatDateTime,
  rejectRentalRequest,
  selectableUnits,
  type RejectReason,
  type RentalRequestStatus,
  type RentalRequestSummary,
  type UnitType,
} from "../../app/rentalRequests";
import type { Navigate } from "../../app/types";
import { rentalOrders } from "../../mocks/fm";
import { Button } from "../../components/ui/Button";
import { DemoNotice } from "../../components/ui/DemoNotice";
import { DetailPanel } from "../../components/ui/DetailPanel";
import { SurfaceState } from "../../components/ui/SurfaceState";

type StatusFilter = "all" | RentalRequestStatus;

const PAGE_SIZE = 20;
/** Cỡ trang tối đa BE cho phép — dùng khi đồng bộ trạng thái sau 409. */
const SYNC_PAGE_SIZE = 100;

const STATUS_LABEL: Record<RentalRequestStatus, string> = {
  Pending: "Chờ xử lý",
  Approved: "Đã duyệt",
  Rejected: "Đã từ chối",
  Converted: "Đã tạo đơn",
  Expired: "Hết hạn",
};

const STATUS_BADGE: Record<RentalRequestStatus, string> = {
  Pending: "bg-warning/14 text-warning",
  Approved: "bg-success/14 text-success",
  Rejected: "bg-danger/14 text-danger",
  Converted: "bg-brand-soft text-brand",
  Expired: "bg-surface-subtle text-muted",
};

const REJECT_REASON_LABEL: Record<RejectReason, string> = {
  UNIT_UNAVAILABLE: "Không còn khoang trống phù hợp",
  UNIT_MISMATCH: "Loại khoang không đáp ứng nhu cầu",
  CUSTOMER_REQUEST: "Khách yêu cầu huỷ",
  OTHER: "Khác (ghi rõ lý do)",
};

const FILTER_SELECT =
  "min-w-[170px] rounded-sm border border-border bg-surface px-[11px] py-[10px] pr-[30px] text-[12px] font-semibold text-ink";

type QueueState =
  | { kind: "loading" }
  | { kind: "no-facility" }
  | { kind: "error"; status: number | null; message: string }
  | { kind: "ready"; items: RentalRequestSummary[]; total: number; page: number };

/** Kết quả FM vừa xử lý trong phiên này — danh sách từ BE không trả khoang đã gán / lý do. */
type HandledResult = { kind: "approved"; unitCode: string } | { kind: "rejected"; reason: string };

function filterStatus(filter: StatusFilter): RentalRequestStatus | undefined {
  return filter === "all" ? undefined : filter;
}

/** Hàm tải thuần (không setState) để effect chỉ ghi state sau `await`. */
async function loadQueue(facilityId: number, filter: StatusFilter): Promise<QueueState> {
  try {
    const data = await fetchRentalRequests(facilityId, filterStatus(filter), 0, PAGE_SIZE);
    return { kind: "ready", items: data.items, total: data.total, page: 0 };
  } catch (error) {
    return error instanceof ApiError
      ? { kind: "error", status: error.status, message: error.message }
      : { kind: "error", status: null, message: "Không tải được hàng chờ, thử lại sau." };
  }
}

export function RentalRequestQueuePage({ navigate }: { navigate: Navigate }) {
  // Banner vẫn đọc mock: chưa có API danh sách đơn thuê cho FM (spec-gap #100).
  const openOrders = rentalOrders.filter((order) => order.status === "OpenForRepropose");
  const facilityId = currentFmFacilityId();
  const [statusFilter, setStatusFilter] = useState<StatusFilter>("Pending");
  const [queue, setQueue] = useState<QueueState>(
    facilityId === null ? { kind: "no-facility" } : { kind: "loading" },
  );
  const [unitTypes, setUnitTypes] = useState<UnitType[]>([]);
  const [isLoadingMore, setIsLoadingMore] = useState(false);
  const [loadMoreError, setLoadMoreError] = useState<string | null>(null);
  const [handled, setHandled] = useState<Record<number, HandledResult>>({});
  const [openId, setOpenId] = useState<number | null>(null);
  const [mode, setMode] = useState<"view" | "reject">("view");
  const [selectedUnitId, setSelectedUnitId] = useState("");
  const [rejectReason, setRejectReason] = useState<RejectReason | "">("");
  const [rejectNote, setRejectNote] = useState("");
  const [busyId, setBusyId] = useState<number | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);

  useEffect(() => {
    if (facilityId === null) return;
    let active = true;
    async function load(id: number) {
      const [next, types] = await Promise.all([
        loadQueue(id, "Pending"),
        fetchUnitTypes(id).catch(() => [] as UnitType[]),
      ]);
      if (!active) return;
      setQueue(next);
      setUnitTypes(types);
    }
    void load(facilityId);
    return () => {
      active = false;
    };
  }, [facilityId]);

  const items = queue.kind === "ready" ? queue.items : [];
  const openRequest = items.find((request) => request.id === openId) ?? null;

  function unitTypeLabel(unitTypeId: number) {
    const type = unitTypes.find((item) => item.id === unitTypeId);
    return type ? `${type.name} · ${type.area} m²` : `Loại khoang #${unitTypeId}`;
  }

  // setState ở event handler, không phải trong effect.
  async function reload(filter: StatusFilter) {
    if (facilityId === null) return;
    setQueue({ kind: "loading" });
    setLoadMoreError(null);
    setQueue(await loadQueue(facilityId, filter));
  }

  function changeFilter(next: StatusFilter) {
    setStatusFilter(next);
    setOpenId(null);
    void reload(next);
  }

  async function loadMore() {
    if (facilityId === null || queue.kind !== "ready") return;
    setIsLoadingMore(true);
    setLoadMoreError(null);
    try {
      const nextPage = queue.page + 1;
      const data = await fetchRentalRequests(
        facilityId,
        filterStatus(statusFilter),
        nextPage,
        PAGE_SIZE,
      );
      setQueue({
        kind: "ready",
        items: [...queue.items, ...data.items],
        total: data.total,
        page: nextPage,
      });
    } catch {
      setLoadMoreError("Không tải thêm được — thử lại sau.");
    } finally {
      setIsLoadingMore(false);
    }
  }

  function openDetails(request: RentalRequestSummary) {
    setOpenId(request.id);
    setMode("view");
    setSelectedUnitId("");
    setRejectReason("");
    setRejectNote("");
    setActionError(null);
  }

  /**
   * Sau 409 (FM khác đã xử lý, quá hạn…): lấy trạng thái mới nhất từ BE và cập nhật đúng các dòng đang hiện,
   * không đổi danh sách để bảng chi tiết vẫn mở cùng câu lỗi. Lỗi khi đồng bộ thì bỏ qua — câu 409 đã hiện.
   */
  async function syncStatusesAfterConflict() {
    if (facilityId === null) return;
    try {
      const latest = await fetchRentalRequests(facilityId, undefined, 0, SYNC_PAGE_SIZE);
      const statusById = new Map(latest.items.map((item) => [item.id, item.status]));
      setQueue((current) =>
        current.kind === "ready"
          ? {
              ...current,
              items: current.items.map((item) => ({
                ...item,
                status: statusById.get(item.id) ?? item.status,
              })),
            }
          : current,
      );
    } catch {
      // giữ nguyên danh sách; FM có thể đổi bộ lọc để tải lại
    }
  }

  function markHandled(requestId: number, status: RentalRequestStatus, result: HandledResult) {
    setQueue((current) =>
      current.kind === "ready"
        ? {
            ...current,
            items: current.items.map((item) =>
              item.id === requestId ? { ...item, status } : item,
            ),
          }
        : current,
    );
    setHandled((current) => ({ ...current, [requestId]: result }));
  }

  async function approve(request: RentalRequestSummary) {
    if (!selectedUnitId) return;
    setBusyId(request.id);
    setActionError(null);
    try {
      const result = await approveRentalRequest(request.id, Number(selectedUnitId));
      const unitCode =
        selectableUnits(facilityId ?? 0, request.unitTypeId).find(
          (unit) => unit.id === result.unitId,
        )?.code ?? `#${result.unitId}`;
      markHandled(request.id, result.status, { kind: "approved", unitCode });
    } catch (error) {
      // 409 (khoang hết Available, FM khác đã xử lý, quá hạn), 403, 404: hiện nguyên câu của BE.
      setActionError(error instanceof ApiError ? error.message : "Không duyệt được, thử lại sau.");
      if (error instanceof ApiError && error.status === 409) await syncStatusesAfterConflict();
    } finally {
      setBusyId(null);
    }
  }

  async function reject(request: RentalRequestSummary) {
    if (!rejectReason || (rejectReason === "OTHER" && !rejectNote.trim())) return;
    setBusyId(request.id);
    setActionError(null);
    try {
      const note = rejectNote.trim();
      const result = await rejectRentalRequest(request.id, rejectReason, note || undefined);
      const label = REJECT_REASON_LABEL[result.rejectReason];
      markHandled(request.id, result.status, {
        kind: "rejected",
        reason: note ? `${label}: ${note}` : label,
      });
      setMode("view");
    } catch (error) {
      setActionError(
        error instanceof ApiError ? error.message : "Không từ chối được, thử lại sau.",
      );
      if (error instanceof ApiError && error.status === 409) await syncStatusesAfterConflict();
    } finally {
      setBusyId(null);
    }
  }

  const availableUnits = openRequest
    ? selectableUnits(facilityId ?? 0, openRequest.unitTypeId)
    : [];
  const canConfirmReject =
    rejectReason !== "" && (rejectReason !== "OTHER" || rejectNote.trim().length > 0);
  const openResult = openRequest ? handled[openRequest.id] : undefined;

  return (
    <main>
      {openOrders.length > 0 && (
        <div className="mb-6">
          <DemoNotice tone="pending">
            <span className="flex flex-wrap items-center justify-between gap-3">
              <span>
                Có {openOrders.length} đơn cần đề xuất lại khoang (khách đã từ chối đề xuất trước).
              </span>
              <button
                className="border-0 bg-transparent p-0 text-[11px] font-extrabold text-brand hover:underline"
                onClick={() => navigate(`/fm/rental-orders/${openOrders[0].id}/re-propose`)}
              >
                Xem ngay <ArrowRight size={14} className="inline" />
              </button>
            </span>
          </DemoNotice>
        </div>
      )}
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
          disabled={queue.kind === "no-facility"}
          onChange={(event) => changeFilter(event.target.value as StatusFilter)}
        >
          <option value="all">Tất cả trạng thái</option>
          <option value="Pending">Chờ xử lý</option>
          <option value="Approved">Đã duyệt</option>
          <option value="Rejected">Đã từ chối</option>
          <option value="Converted">Đã tạo đơn</option>
          <option value="Expired">Hết hạn</option>
        </select>
      </div>

      {queue.kind === "loading" && <SurfaceState variant="loading" title="Đang tải hàng chờ…" />}

      {queue.kind === "no-facility" && (
        <SurfaceState
          variant="forbidden"
          title="Chưa xác định được cơ sở của bạn"
          description="Tài khoản này chưa được gán cơ sở để xem hàng chờ yêu cầu đặt kho."
        />
      )}

      {queue.kind === "error" && (
        <SurfaceState
          variant={queue.status === 403 ? "forbidden" : "error"}
          title={
            queue.status === 403 ? "Bạn không có quyền xem hàng chờ này" : "Không tải được hàng chờ"
          }
          description={queue.message}
          action={
            queue.status === 403
              ? undefined
              : { label: "Thử lại", onClick: () => void reload(statusFilter) }
          }
        />
      )}

      {queue.kind === "ready" && items.length === 0 && (
        <SurfaceState variant="empty" title="Không có yêu cầu nào phù hợp bộ lọc" />
      )}

      {queue.kind === "ready" && items.length > 0 && (
        <div className="grid gap-3">
          {items.map((request) => (
            <button
              key={request.id}
              className="flex items-start justify-between gap-5 border border-border bg-surface p-5 text-left transition-colors hover:border-brand max-[760px]:flex-col"
              onClick={() => openDetails(request)}
            >
              <div>
                <p className="m-0 text-[15px] font-bold text-ink">{request.customerName}</p>
                <p className="mb-0 mt-1.5 text-[13px] text-muted">
                  {unitTypeLabel(request.unitTypeId)} · từ {formatDate(request.startDate)} ·{" "}
                  {request.period} tháng
                </p>
                <p className="mb-0 mt-1 font-mono text-[10px] uppercase tracking-[0.06em] text-muted">
                  Gửi lúc {formatDateTime(request.createdAt)}
                </p>
              </div>
              <span
                className={`whitespace-nowrap rounded-full px-[9px] py-[6px] text-[11px] font-extrabold ${STATUS_BADGE[request.status]}`}
              >
                {STATUS_LABEL[request.status]}
              </span>
            </button>
          ))}
          {items.length < queue.total && (
            <div className="flex flex-col items-center gap-2 pt-2">
              <Button variant="secondary" pending={isLoadingMore} onClick={() => void loadMore()}>
                Tải thêm ({items.length}/{queue.total})
              </Button>
              {loadMoreError && <p className="m-0 text-[12px] text-danger">{loadMoreError}</p>}
            </div>
          )}
        </div>
      )}

      {openRequest && (
        <DetailPanel
          title={openRequest.customerName}
          eyebrow={`Yêu cầu · ${unitTypeLabel(openRequest.unitTypeId)}`}
          onClose={() => setOpenId(null)}
        >
          <div className="grid gap-2.5 text-[13px] text-muted">
            <span className="flex items-center gap-2">
              <Phone size={14} /> {openRequest.customerPhone}
            </span>
            <span className="flex items-center gap-2">
              <Mail size={14} /> {openRequest.customerEmail}
            </span>
          </div>
          <p className="mb-0 mt-4 text-[13px] leading-[1.6] text-ink">
            Bắt đầu thuê {formatDate(openRequest.startDate)} · {openRequest.period} tháng
            {openRequest.expiresAt && <> · Hạn xử lý {formatDateTime(openRequest.expiresAt)}</>}
          </p>

          {actionError && (
            <div className="mt-5">
              <DemoNotice tone="error">{actionError}</DemoNotice>
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
                  {availableUnits.map((unit) => (
                    <option key={unit.id} value={unit.id}>
                      {unit.code}
                    </option>
                  ))}
                </select>
              </label>
              {availableUnits.length === 0 && (
                <p className="mb-0 mt-2 text-[12px] text-danger">
                  Hiện không còn khoang trống đúng loại yêu cầu.
                </p>
              )}
              <div className="mt-5 flex items-center gap-3">
                <Button
                  pending={busyId === openRequest.id}
                  disabled={!selectedUnitId}
                  onClick={() => void approve(openRequest)}
                >
                  {busyId !== openRequest.id && <Check size={16} />}
                  Duyệt &amp; gán khoang
                </Button>
                <Button variant="secondary" onClick={() => setMode("reject")}>
                  <X size={16} /> Từ chối
                </Button>
              </div>
            </>
          )}

          {openRequest.status === "Pending" && mode === "reject" && (
            <div className="mt-6 border border-border bg-surface-subtle p-4">
              <label className="grid gap-1.5 text-[12px] font-bold text-ink">
                Lý do từ chối
                <select
                  className="rounded-sm border border-border bg-surface px-3 py-2.5 text-[13px] font-normal text-ink"
                  value={rejectReason}
                  onChange={(event) => setRejectReason(event.target.value as RejectReason | "")}
                >
                  <option value="">— Chọn lý do —</option>
                  {(Object.keys(REJECT_REASON_LABEL) as RejectReason[]).map((reason) => (
                    <option key={reason} value={reason}>
                      {REJECT_REASON_LABEL[reason]}
                    </option>
                  ))}
                </select>
              </label>
              <label className="mt-3 grid gap-1.5 text-[12px] font-bold text-ink">
                {rejectReason === "OTHER" ? "Ghi chú (bắt buộc)" : "Ghi chú (không bắt buộc)"}
                <textarea
                  className="min-h-[80px] rounded-sm border border-border bg-surface p-2.5 text-[13px] font-normal text-ink"
                  value={rejectNote}
                  onChange={(event) => setRejectNote(event.target.value)}
                  placeholder="Cho khách biết vì sao yêu cầu chưa phù hợp…"
                />
              </label>
              <div className="mt-3 flex items-center gap-2.5">
                <Button
                  pending={busyId === openRequest.id}
                  disabled={!canConfirmReject}
                  onClick={() => void reject(openRequest)}
                >
                  {busyId !== openRequest.id && <AlertTriangle size={16} />} Xác nhận từ chối
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

          {openRequest.status === "Approved" && (
            <div className="mt-6">
              <DemoNotice tone="success">
                {openResult?.kind === "approved"
                  ? `Đã gán khoang ${openResult.unitCode} cho yêu cầu này.`
                  : "Yêu cầu này đã được duyệt."}
              </DemoNotice>
            </div>
          )}
          {openRequest.status === "Rejected" && (
            <div className="mt-6">
              <DemoNotice tone="error">
                {openResult?.kind === "rejected" ? openResult.reason : "Yêu cầu này đã bị từ chối."}
              </DemoNotice>
            </div>
          )}
        </DetailPanel>
      )}
    </main>
  );
}
