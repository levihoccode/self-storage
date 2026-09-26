import { ArrowRight, CalendarClock, Loader2 } from "lucide-react";
import { useState } from "react";
import type { Navigate } from "../app/types";
import { facilities, formatPrice } from "../mocks/catalog";
import {
  INVOICE_TYPE_LABEL,
  type Invoice,
  type InvoiceStatus,
  type InvoiceType,
  invoices,
  isInvoicePaid,
  markInvoicePaid,
} from "../mocks/invoices";
import { DemoNotice } from "../components/ui/DemoNotice";
import { DetailPanel } from "../components/ui/DetailPanel";
import { SurfaceState } from "../components/ui/SurfaceState";

const STATUS_BADGE: Record<InvoiceStatus, string> = {
  Unpaid: "bg-[rgba(230,238,201,0.18)] text-warning",
  Paid: "bg-[rgba(168,207,154,0.18)] text-success",
  Canceled: "bg-surface-subtle text-muted",
};
const STATUS_LABEL: Record<InvoiceStatus, string> = {
  Unpaid: "Chưa thanh toán",
  Paid: "Đã thanh toán",
  Canceled: "Đã huỷ",
};
const PRIMARY_BUTTON =
  "inline-flex min-h-11 items-center justify-center gap-2.5 rounded-sm border border-transparent bg-brand px-[18px] text-[14px] font-[750] text-background transition-colors duration-[180ms] ease hover:bg-brand-strong disabled:cursor-not-allowed disabled:opacity-60";
const FILTER_SELECT =
  "min-w-[170px] rounded-sm border border-border bg-surface px-[11px] py-[10px] pr-[30px] text-[12px] font-semibold text-ink max-[760px]:w-full";

function effectiveStatus(invoice: Invoice): InvoiceStatus {
  if (invoice.status === "Canceled") return "Canceled";
  return isInvoicePaid(invoice.id) ? "Paid" : "Unpaid";
}

export function InvoicesPage({ navigate }: { navigate: Navigate }) {
  const [typeFilter, setTypeFilter] = useState<"all" | InvoiceType>("all");
  const [statusFilter, setStatusFilter] = useState<"all" | InvoiceStatus>("all");
  const [openId, setOpenId] = useState<string | null>(null);
  const [payingId, setPayingId] = useState<string | null>(null);
  const [outcome, setOutcome] = useState<{ id: string; ok: boolean } | null>(null);
  const [, refresh] = useState(0);

  const list = invoices.filter((invoice) => {
    if (typeFilter !== "all" && invoice.type !== typeFilter) return false;
    if (statusFilter !== "all" && effectiveStatus(invoice) !== statusFilter) return false;
    return true;
  });

  const openInvoice = invoices.find((invoice) => invoice.id === openId) ?? null;

  function pay(invoice: Invoice) {
    setPayingId(invoice.id);
    setOutcome(null);
    window.setTimeout(() => {
      setPayingId(null);
      if (invoice.simulateFailure) {
        setOutcome({ id: invoice.id, ok: false });
        return;
      }
      markInvoicePaid(invoice.id);
      refresh((n) => n + 1);
      setOutcome({ id: invoice.id, ok: true });
    }, 900);
  }

  return (
    <main>
      <div className="mb-8">
        <h1 className="m-0 text-[28px] tracking-[-0.03em] text-ink">Hóa đơn &amp; thanh toán</h1>
        <p className="mt-2 text-[13px] text-muted">
          Theo dõi và thanh toán mọi khoản phát sinh trong suốt vòng đời thuê kho.
        </p>
      </div>

      <div className="mb-6 flex items-center gap-2.5 max-[760px]:flex-col max-[760px]:items-stretch">
        <select
          className={FILTER_SELECT}
          value={typeFilter}
          onChange={(event) => setTypeFilter(event.target.value as "all" | InvoiceType)}
        >
          <option value="all">Tất cả loại hoá đơn</option>
          {Object.entries(INVOICE_TYPE_LABEL).map(([value, label]) => (
            <option key={value} value={value}>
              {label}
            </option>
          ))}
        </select>
        <select
          className={FILTER_SELECT}
          value={statusFilter}
          onChange={(event) => setStatusFilter(event.target.value as "all" | InvoiceStatus)}
        >
          <option value="all">Tất cả trạng thái</option>
          <option value="Unpaid">Chưa thanh toán</option>
          <option value="Paid">Đã thanh toán</option>
          <option value="Canceled">Đã huỷ</option>
        </select>
      </div>

      {list.length === 0 ? (
        <SurfaceState variant="empty" title="Không có hoá đơn phù hợp bộ lọc" />
      ) : (
        <div className="grid gap-3">
          {list.map((invoice) => {
            const status = effectiveStatus(invoice);
            return (
              <button
                key={invoice.id}
                className="flex items-center justify-between gap-5 border border-border bg-surface p-5 text-left transition-colors hover:border-brand max-[760px]:flex-col max-[760px]:items-stretch"
                onClick={() => {
                  setOpenId(invoice.id);
                  setOutcome(null);
                }}
              >
                <div>
                  <p className="m-0 font-mono text-[11px] font-bold uppercase tracking-[0.08em] text-brand">
                    {invoice.code} · {INVOICE_TYPE_LABEL[invoice.type]}
                  </p>
                  <h3 className="mb-0 mt-1 text-[16px] tracking-[-0.02em]">{invoice.title}</h3>
                  <p className="mb-0 mt-1.5 flex items-center gap-[6px] text-[12px] text-muted">
                    <CalendarClock size={14} /> Hạn thanh toán {invoice.dueDate}
                  </p>
                </div>
                <div className="flex items-center gap-5 max-[760px]:justify-between">
                  <strong className="text-[16px]">{formatPrice(invoice.amount)}đ</strong>
                  <span
                    className={`whitespace-nowrap rounded-full px-[9px] py-[6px] text-[11px] font-extrabold ${STATUS_BADGE[status]}`}
                  >
                    {STATUS_LABEL[status]}
                  </span>
                </div>
              </button>
            );
          })}
        </div>
      )}

      {openInvoice && (
        <DetailPanel
          title={openInvoice.title}
          eyebrow={`${openInvoice.code} · ${INVOICE_TYPE_LABEL[openInvoice.type]}`}
          onClose={() => setOpenId(null)}
        >
          <p className="m-0 text-[13px] leading-[1.6] text-muted">{openInvoice.description}</p>
          <dl className="mt-5 grid gap-3">
            <div className="flex items-center justify-between gap-4 text-[13px]">
              <dt className="text-muted">Khoang liên quan</dt>
              <dd className="m-0 font-bold text-ink">{openInvoice.unitCode}</dd>
            </div>
            <div className="flex items-center justify-between gap-4 text-[13px]">
              <dt className="text-muted">Cơ sở</dt>
              <dd className="m-0 font-bold text-ink">
                {facilities.find((facility) => facility.id === openInvoice.facilityId)?.name ?? "—"}
              </dd>
            </div>
            <div className="flex items-center justify-between gap-4 text-[13px]">
              <dt className="text-muted">Số tiền</dt>
              <dd className="m-0 font-bold text-ink">{formatPrice(openInvoice.amount)}đ</dd>
            </div>
            {openInvoice.discountAmount > 0 && (
              <div className="flex items-center justify-between gap-4 text-[13px]">
                <dt className="text-muted">Giảm giá</dt>
                <dd className="m-0 font-bold text-success">
                  -{formatPrice(openInvoice.discountAmount)}đ
                </dd>
              </div>
            )}
            <div className="flex items-center justify-between gap-4 text-[13px]">
              <dt className="text-muted">Hạn thanh toán</dt>
              <dd className="m-0 font-bold text-ink">{openInvoice.dueDate}</dd>
            </div>
          </dl>

          {outcome?.id === openInvoice.id && outcome.ok && (
            <div className="mt-5">
              <DemoNotice tone="success">Thanh toán thành công.</DemoNotice>
              {openInvoice.type === "Deposit" && (
                <button
                  className={`${PRIMARY_BUTTON} mt-3.5`}
                  onClick={() => navigate(`/appointments/new?orderId=${openInvoice.orderId}`)}
                >
                  Đặt lịch check-in <ArrowRight size={16} />
                </button>
              )}
            </div>
          )}

          {outcome?.id === openInvoice.id && !outcome.ok && (
            <div className="mt-5">
              <DemoNotice tone="error">
                Khoang {openInvoice.unitCode} vừa có người khác đặt cọc trước. Đội ngũ vận hành sẽ
                gửi đề xuất khoang khác cho bạn.
              </DemoNotice>
            </div>
          )}

          {effectiveStatus(openInvoice) === "Unpaid" && !outcome && (
            <button
              className={`${PRIMARY_BUTTON} mt-6 w-full`}
              disabled={payingId === openInvoice.id}
              onClick={() => pay(openInvoice)}
            >
              {payingId === openInvoice.id ? (
                <>
                  <Loader2
                    className="animate-[surface-state-spin_0.9s_linear_infinite]"
                    size={16}
                  />
                  Đang chuyển đến cổng thanh toán…
                </>
              ) : (
                "Tiến hành thanh toán"
              )}
            </button>
          )}
        </DetailPanel>
      )}
    </main>
  );
}
