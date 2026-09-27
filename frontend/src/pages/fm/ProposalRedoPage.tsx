import { AlertTriangle, ArrowLeft, Check, History, Loader2, UserRound } from "lucide-react";
import { useState } from "react";
import type { Navigate } from "../../app/types";
import { formatPrice, unitTypes } from "../../mocks/catalog";
import {
  fmUnits,
  MAX_ORDER_REJECTIONS,
  rentalOrders,
  type ProposalFeedbackEntry,
} from "../../mocks/fm";
import { DemoNotice } from "../../components/ui/DemoNotice";
import { SurfaceState } from "../../components/ui/SurfaceState";

const PRIMARY_BUTTON =
  "inline-flex min-h-11 items-center justify-center gap-2.5 rounded-sm border border-transparent bg-brand px-[18px] text-[14px] font-[750] text-background transition-colors duration-[180ms] ease hover:bg-brand-strong disabled:cursor-not-allowed disabled:opacity-60";

const SOURCE_LABEL: Record<ProposalFeedbackEntry["source"], string> = {
  customer: "Khách từ chối",
  checkin: "Từ chối tại chỗ lúc check-in",
};

function getOrderId() {
  // /fm/rental-orders/:id/re-propose — id is the second-to-last segment.
  const segments = window.location.pathname.split("/").filter(Boolean);
  return segments[segments.length - 2] ?? "";
}

function unitTypeName(unitTypeId: string) {
  return unitTypes.find((type) => type.id === unitTypeId)?.name ?? unitTypeId;
}

export function ProposalRedoPage({ navigate }: { navigate: Navigate }) {
  const orderId = getOrderId();
  const order = rentalOrders.find((item) => item.id === orderId);

  const [selectedUnitId, setSelectedUnitId] = useState("");
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [sent, setSent] = useState(false);

  if (!order) {
    return (
      <main>
        <SurfaceState
          variant="not-found"
          title="Không tìm thấy đơn hàng"
          action={{ label: "Về hàng chờ yêu cầu", onClick: () => navigate("/fm/rental-requests") }}
        />
      </main>
    );
  }

  const availableUnits = fmUnits.filter(
    (unit) => unit.unitTypeId === order.unitTypeId && unit.status === "Available",
  );
  const selectedUnit = fmUnits.find((unit) => unit.id === selectedUnitId);
  const newDepositEstimate = selectedUnit
    ? (unitTypes.find((type) => type.id === selectedUnit.unitTypeId)?.monthlyPrice ?? 0)
    : 0;
  const depositDiff = order.afterDeposit ? newDepositEstimate - (order.previousDeposit ?? 0) : 0;

  function submit() {
    if (!selectedUnitId) return;
    setIsSubmitting(true);
    window.setTimeout(() => {
      setIsSubmitting(false);
      setSent(true);
    }, 700);
  }

  return (
    <main>
      <button
        className="mb-6 inline-flex items-center gap-2 border-0 bg-transparent p-0 text-[12px] font-bold text-muted hover:text-brand"
        onClick={() => navigate("/fm/rental-requests")}
      >
        <ArrowLeft size={16} /> Về hàng chờ yêu cầu
      </button>

      <div className="mb-8">
        <p className="m-0 flex items-center gap-2 font-mono text-[11px] font-bold uppercase tracking-[0.08em] text-brand">
          <UserRound size={14} /> {order.customerName}
        </p>
        <h1 className="mb-0 mt-1.5 text-[28px] tracking-[-0.03em] text-ink">Đề xuất lại khoang</h1>
        <p className="mt-2 text-[13px] text-muted">
          Cần: {unitTypeName(order.unitTypeId)} · {order.desiredSize}
        </p>
      </div>

      <div className="grid grid-cols-[1fr_360px] gap-8 max-[980px]:grid-cols-1">
        <div className="grid gap-6">
          <section className="border border-border bg-surface p-6">
            <h2 className="m-0 flex items-center gap-2 text-[15px] font-bold text-ink">
              <History size={16} /> Lịch sử đề xuất trước
            </h2>
            <div className="mt-4 grid gap-3">
              {order.rejectionHistory.map((entry, index) => (
                <div key={index} className="border-l-2 border-danger pl-3.5">
                  <p className="m-0 flex items-center gap-2 text-[12px] font-bold text-ink">
                    Khoang {entry.unitCode}
                    <span className="rounded-full bg-danger/14 px-2 py-0.5 text-[10px] font-bold text-danger">
                      {SOURCE_LABEL[entry.source]}
                    </span>
                  </p>
                  <p className="mb-0 mt-1 text-[12px] text-muted">{entry.note}</p>
                  <p className="mb-0 mt-1 font-mono text-[10px] uppercase text-muted">
                    {entry.rejectedAt}
                  </p>
                </div>
              ))}
            </div>
            <p className="mb-0 mt-4 text-[12px] font-bold text-muted">
              Đã từ chối {order.rejectionHistory.length}/{MAX_ORDER_REJECTIONS} lần cho phép.
            </p>
          </section>

          {order.status === "Canceled" ? (
            <SurfaceState
              variant="forbidden"
              title="Đơn đã bị huỷ"
              description="Đã đạt ngưỡng tối đa số lần từ chối — hệ thống tự huỷ đơn. Chỉ có thể xem lại lịch sử, không thể đề xuất tiếp."
            />
          ) : sent ? (
            <DemoNotice tone="success">
              Đã gửi đề xuất khoang {selectedUnit?.code} cho khách. Khách sẽ nhận thông báo để xác
              nhận.
            </DemoNotice>
          ) : (
            <section className="border border-border bg-surface p-6">
              <h2 className="m-0 text-[15px] font-bold text-ink">Chọn khoang mới</h2>
              {order.afterDeposit && (
                <div className="mt-4">
                  <DemoNotice tone="pending">
                    Khách đã đặt cọc trước đó ({formatPrice(order.previousDeposit ?? 0)}đ) — cần đổi
                    khoang do phát sinh sự cố. Kiểm tra chênh lệch cọc bên dưới trước khi gửi.
                  </DemoNotice>
                </div>
              )}
              <label className="mt-5 grid gap-1.5 text-[12px] font-bold text-ink">
                Khoang trống phù hợp
                <select
                  className="rounded-sm border border-border bg-surface-subtle px-3 py-2.5 text-[13px] text-ink"
                  value={selectedUnitId}
                  onChange={(event) => setSelectedUnitId(event.target.value)}
                >
                  <option value="">— Chọn khoang —</option>
                  {availableUnits.map((unit) => (
                    <option key={unit.id} value={unit.id}>
                      {unit.code} · {unit.size}
                    </option>
                  ))}
                </select>
              </label>
              {availableUnits.length === 0 && (
                <p className="mb-0 mt-2 flex items-center gap-2 text-[12px] text-danger">
                  <AlertTriangle size={14} /> Hiện không còn khoang trống đúng loại yêu cầu.
                </p>
              )}
              {order.afterDeposit && selectedUnit && (
                <div className="mt-4 grid gap-2 border border-border bg-surface-subtle p-3.5 text-[12px]">
                  <div className="flex items-center justify-between">
                    <span className="text-muted">Cọc đã thu (khoang cũ)</span>
                    <strong>{formatPrice(order.previousDeposit ?? 0)}đ</strong>
                  </div>
                  <div className="flex items-center justify-between">
                    <span className="text-muted">Cọc ước tính (khoang mới)</span>
                    <strong>{formatPrice(newDepositEstimate)}đ</strong>
                  </div>
                  <div className="flex items-center justify-between border-t border-border pt-2">
                    <span className="text-muted">Chênh lệch</span>
                    <strong className={depositDiff >= 0 ? "text-danger" : "text-success"}>
                      {depositDiff >= 0 ? "+" : ""}
                      {formatPrice(depositDiff)}đ
                    </strong>
                  </div>
                </div>
              )}
              <button
                className={`${PRIMARY_BUTTON} mt-5`}
                disabled={!selectedUnitId || isSubmitting}
                onClick={submit}
              >
                {isSubmitting ? (
                  <Loader2
                    className="animate-[surface-state-spin_0.9s_linear_infinite]"
                    size={16}
                  />
                ) : (
                  <Check size={16} />
                )}
                Đề xuất lại
              </button>
            </section>
          )}
        </div>

        <aside className="grid content-start gap-2 border border-border bg-surface p-5 text-[13px]">
          <p className="m-0 font-mono text-[10px] font-bold uppercase tracking-[0.08em] text-brand">
            Tóm tắt yêu cầu
          </p>
          <div className="grid gap-[3px]">
            <span className="font-mono text-[10px] uppercase text-muted">Loại kho</span>
            <strong>{unitTypeName(order.unitTypeId)}</strong>
          </div>
          <div className="grid gap-[3px]">
            <span className="font-mono text-[10px] uppercase text-muted">Diện tích mong muốn</span>
            <strong>{order.desiredSize}</strong>
          </div>
        </aside>
      </div>
    </main>
  );
}
