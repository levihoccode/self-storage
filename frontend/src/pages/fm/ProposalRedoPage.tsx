import { AlertTriangle, ArrowLeft, Check, History, UserRound } from "lucide-react";
import { useEffect, useState } from "react";
import { ApiError } from "../../app/api";
import { fetchRentalOrder, formatDate, formatMoney, reproposeUnit } from "../../app/proposals";
import type { FmRentalOrder, Navigate, ProposalSnapshot, ProposalStatus } from "../../app/types";
import { Button } from "../../components/ui/Button";
import { DemoNotice } from "../../components/ui/DemoNotice";
import { SurfaceState } from "../../components/ui/SurfaceState";

type LoadState =
  | { kind: "loading" }
  | { kind: "ready"; order: FmRentalOrder }
  | { kind: "not-found" }
  | { kind: "forbidden"; message: string }
  | { kind: "error"; message: string };

const STATUS_LABEL: Record<ProposalStatus, string> = {
  Pending: "Chờ khách phản hồi",
  Agreed: "Khách đã đồng ý",
  Rejected: "Khách từ chối",
  Canceled: "Đã huỷ",
};

const STATUS_CLASS: Record<ProposalStatus, string> = {
  Pending: "bg-surface-subtle text-muted",
  Agreed: "bg-success/14 text-success",
  Rejected: "bg-danger/14 text-danger",
  Canceled: "bg-surface-subtle text-muted",
};

function getOrderId(): number {
  // /fm/rental-orders/:id/re-propose — id is the second-to-last segment.
  const segments = window.location.pathname.split("/").filter(Boolean);
  return Number(segments[segments.length - 2]);
}

/** Tải đơn và đổi lỗi API thành trạng thái màn hình (404 / 403 / lỗi khác). */
async function loadOrder(orderId: number): Promise<LoadState> {
  if (!Number.isInteger(orderId) || orderId <= 0) return { kind: "not-found" };
  try {
    return { kind: "ready", order: await fetchRentalOrder(orderId) };
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) return { kind: "not-found" };
    if (error instanceof ApiError && error.status === 403) {
      return { kind: "forbidden", message: error.message };
    }
    return {
      kind: "error",
      message: error instanceof Error ? error.message : "Không tải được đơn thuê.",
    };
  }
}

export function ProposalRedoPage({ navigate }: { navigate: Navigate }) {
  const orderId = getOrderId();
  const [state, setState] = useState<LoadState>({ kind: "loading" });
  const [selectedUnitId, setSelectedUnitId] = useState("");
  const [overrideReason, setOverrideReason] = useState("");
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState("");
  const [sent, setSent] = useState<{ proposal: ProposalSnapshot; unitCode: string } | null>(null);

  // setState chỉ chạy sau await; `active` chặn cập nhật khi đã rời trang.
  useEffect(() => {
    let active = true;
    async function loadInitial() {
      const next = await loadOrder(orderId);
      if (active) setState(next);
    }
    void loadInitial();
    return () => {
      active = false;
    };
  }, [orderId]);

  // Nút "Thử lại" — setState nằm ở event handler, không phải effect.
  async function reload() {
    setState({ kind: "loading" });
    setState(await loadOrder(orderId));
  }

  const backToQueue = () => navigate("/fm/rental-requests");

  if (state.kind === "loading") {
    return (
      <main>
        <SurfaceState variant="loading" title="Đang tải đơn thuê…" />
      </main>
    );
  }
  if (state.kind === "not-found") {
    return (
      <main>
        <SurfaceState
          variant="not-found"
          title="Không tìm thấy đơn hàng"
          action={{ label: "Về hàng chờ yêu cầu", onClick: backToQueue }}
        />
      </main>
    );
  }
  if (state.kind === "forbidden") {
    return (
      <main>
        <SurfaceState
          variant="forbidden"
          title="Không có quyền xem đơn này"
          description={state.message}
          action={{ label: "Về hàng chờ yêu cầu", onClick: backToQueue }}
        />
      </main>
    );
  }
  if (state.kind === "error") {
    return (
      <main>
        <SurfaceState
          variant="error"
          title="Không tải được đơn thuê"
          description={state.message}
          action={{ label: "Thử lại", onClick: () => void reload() }}
        />
      </main>
    );
  }

  const { order } = state;
  const selectedUnit = order.availableUnits.find((unit) => String(unit.id) === selectedUnitId);
  const needsOverride = selectedUnit?.previouslyRejected ?? false;
  const hasPending = order.proposals.some((proposal) => proposal.status === "Pending");
  const isClosed = order.status === "Canceled" || order.status === "Done";

  async function submit() {
    if (!selectedUnit) return;
    setIsSubmitting(true);
    setSubmitError("");
    try {
      const proposal = await reproposeUnit(
        order.id,
        selectedUnit.id,
        needsOverride ? overrideReason.trim() : undefined,
      );
      setSent({ proposal, unitCode: selectedUnit.code });
    } catch (error) {
      setSubmitError(error instanceof Error ? error.message : "Không gửi được đề xuất.");
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <main>
      <button
        className="mb-6 inline-flex items-center gap-2 border-0 bg-transparent p-0 text-[12px] font-bold text-muted hover:text-brand"
        onClick={backToQueue}
      >
        <ArrowLeft size={16} /> Về hàng chờ yêu cầu
      </button>

      <div className="mb-8">
        <p className="m-0 flex items-center gap-2 font-mono text-[11px] font-bold uppercase tracking-[0.08em] text-brand">
          <UserRound size={14} /> {order.customer.name} · Đơn {order.id}
        </p>
        <h1 className="mb-0 mt-1.5 text-[28px] tracking-[-0.03em] text-ink">Đề xuất lại khoang</h1>
        <p className="mt-2 text-[13px] text-muted">
          Cần: {order.requested.unitTypeName} · {order.requested.period} tháng từ{" "}
          {formatDate(order.requested.startDate)}
        </p>
      </div>

      <div className="grid grid-cols-[1fr_360px] gap-8 max-[980px]:grid-cols-1">
        <div className="grid gap-6">
          <section className="border border-border bg-surface p-6">
            <h2 className="m-0 flex items-center gap-2 text-[15px] font-bold text-ink">
              <History size={16} /> Lịch sử đề xuất trước
            </h2>
            {order.proposals.length === 0 ? (
              <p className="mb-0 mt-4 text-[12px] text-muted">Đơn này chưa có đề xuất nào.</p>
            ) : (
              <div className="mt-4 grid gap-3">
                {order.proposals.map((entry) => (
                  <div key={entry.id} className="border-l-2 border-border pl-3.5">
                    <p className="m-0 flex items-center gap-2 text-[12px] font-bold text-ink">
                      Khoang {entry.unitCode}
                      <span
                        className={`rounded-full px-2 py-0.5 text-[10px] font-bold ${STATUS_CLASS[entry.status]}`}
                      >
                        {STATUS_LABEL[entry.status]}
                      </span>
                    </p>
                    {entry.note && <p className="mb-0 mt-1 text-[12px] text-muted">{entry.note}</p>}
                    <p className="mb-0 mt-1 font-mono text-[10px] uppercase text-muted">
                      {formatDate(entry.createdAt)}
                    </p>
                  </div>
                ))}
              </div>
            )}
            <p className="mb-0 mt-4 text-[12px] font-bold text-muted">
              Đã từ chối {order.rejectionCount}/{order.maxRejectionCount} lần cho phép.
            </p>
          </section>

          {isClosed ? (
            <SurfaceState
              variant="forbidden"
              title="Đơn đã đóng"
              description={
                order.cancelReason
                  ? `Đơn đã bị huỷ: ${order.cancelReason}. Chỉ có thể xem lại lịch sử.`
                  : "Đơn đã hoàn tất, không thể đề xuất tiếp."
              }
            />
          ) : sent ? (
            <DemoNotice tone="success">
              Đã gửi đề xuất khoang {sent.unitCode} cho khách. Khách sẽ nhận thông báo để xác nhận.
            </DemoNotice>
          ) : hasPending ? (
            <DemoNotice tone="pending">
              Đơn đang có đề xuất chờ khách phản hồi. Chờ khách đồng ý hoặc từ chối trước khi đề
              xuất khoang khác.
            </DemoNotice>
          ) : (
            <section className="border border-border bg-surface p-6">
              <h2 className="m-0 text-[15px] font-bold text-ink">Chọn khoang mới</h2>
              <label className="mt-5 grid gap-1.5 text-[12px] font-bold text-ink">
                Khoang trống phù hợp
                <select
                  className="rounded-sm border border-border bg-surface-subtle px-3 py-2.5 text-[13px] text-ink"
                  value={selectedUnitId}
                  onChange={(event) => {
                    setSelectedUnitId(event.target.value);
                    setSubmitError("");
                  }}
                >
                  <option value="">— Chọn khoang —</option>
                  {order.availableUnits.map((unit) => (
                    <option key={unit.id} value={unit.id}>
                      {unit.code}
                      {unit.size ? ` · ${unit.size}` : ""} · {formatMoney(unit.monthlyPrice)}
                      {unit.previouslyRejected ? " (khách đã từ chối)" : ""}
                    </option>
                  ))}
                </select>
              </label>
              {order.availableUnits.length === 0 && (
                <p className="mb-0 mt-2 flex items-center gap-2 text-[12px] text-danger">
                  <AlertTriangle size={14} /> Hiện không còn khoang trống đúng loại yêu cầu.
                </p>
              )}
              {needsOverride && (
                <label className="mt-4 grid gap-1.5 text-[12px] font-bold text-ink">
                  Lý do đề xuất lại khoang khách đã từ chối
                  <textarea
                    className="min-h-[70px] rounded-sm border border-border bg-surface p-2.5 text-[13px] font-normal text-ink"
                    value={overrideReason}
                    onChange={(event) => setOverrideReason(event.target.value)}
                    placeholder="Lý do sẽ được ghi vào nhật ký thao tác (AuditLog)…"
                  />
                </label>
              )}
              {submitError && (
                <div className="mt-4">
                  <DemoNotice tone="error">{submitError}</DemoNotice>
                </div>
              )}
              <Button
                className="mt-5"
                pending={isSubmitting}
                disabled={!selectedUnit || (needsOverride && !overrideReason.trim())}
                onClick={() => void submit()}
              >
                {!isSubmitting && <Check size={16} />}
                Đề xuất lại
              </Button>
            </section>
          )}
        </div>

        <aside className="grid content-start gap-2 border border-border bg-surface p-5 text-[13px]">
          <p className="m-0 font-mono text-[10px] font-bold uppercase tracking-[0.08em] text-brand">
            Tóm tắt yêu cầu
          </p>
          <div className="grid gap-[3px]">
            <span className="font-mono text-[10px] uppercase text-muted">Khách hàng</span>
            <strong>{order.customer.name}</strong>
            <span className="text-[12px] text-muted">
              {order.customer.email} · {order.customer.phone}
            </span>
          </div>
          <div className="grid gap-[3px]">
            <span className="font-mono text-[10px] uppercase text-muted">Loại kho</span>
            <strong>{order.requested.unitTypeName}</strong>
          </div>
          <div className="grid gap-[3px]">
            <span className="font-mono text-[10px] uppercase text-muted">Trạng thái đơn</span>
            <strong>{order.status}</strong>
          </div>
        </aside>
      </div>
    </main>
  );
}
