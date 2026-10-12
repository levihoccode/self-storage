import { CircleDollarSign, UserRound, Wallet } from "lucide-react";
import { useState } from "react";
import { formatPrice } from "../../mocks/catalog";
import { fmInvoices, type InvoiceStatus, type InvoiceType } from "../../mocks/fm";
import { DetailPanel } from "../../components/ui/DetailPanel";
import { SurfaceState } from "../../components/ui/SurfaceState";

type TypeFilter = "all" | InvoiceType;
type StatusFilter = "all" | InvoiceStatus;

const TYPE_LABEL: Record<InvoiceType, string> = {
  Deposit: "Đặt cọc",
  Rental: "Thuê kho",
  Extension: "Gia hạn",
  Penalty: "Phạt",
  Service: "Dịch vụ",
};

const STATUS_LABEL: Record<InvoiceStatus, string> = {
  Unpaid: "Chưa thanh toán",
  Paid: "Đã thanh toán",
  Canceled: "Đã huỷ",
};

const STATUS_BADGE: Record<InvoiceStatus, string> = {
  Unpaid: "bg-warning/14 text-warning",
  Paid: "bg-success/14 text-success",
  Canceled: "bg-surface-subtle text-muted",
};

const FILTER_SELECT =
  "min-w-[170px] rounded-sm border border-border bg-surface px-[11px] py-[10px] pr-[30px] text-[12px] font-semibold text-ink";

export function FacilityInvoicesPage() {
  const [typeFilter, setTypeFilter] = useState<TypeFilter>("all");
  const [statusFilter, setStatusFilter] = useState<StatusFilter>("all");
  const [openId, setOpenId] = useState<string | null>(null);

  const list = fmInvoices.filter(
    (invoice) =>
      (typeFilter === "all" || invoice.type === typeFilter) &&
      (statusFilter === "all" || invoice.status === statusFilter),
  );
  const openInvoice = fmInvoices.find((invoice) => invoice.id === openId) ?? null;

  const totalUnpaid = fmInvoices
    .filter((invoice) => invoice.status === "Unpaid")
    .reduce((sum, invoice) => sum + invoice.amount, 0);
  const totalPaid = fmInvoices
    .filter((invoice) => invoice.status === "Paid")
    .reduce((sum, invoice) => sum + invoice.amount, 0);

  return (
    <main>
      <div className="mb-8">
        <h1 className="m-0 text-[28px] tracking-[-0.03em] text-ink">Quản lý hóa đơn cơ sở</h1>
        <p className="mt-2 text-[13px] text-muted">
          Hóa đơn phát sinh từ khách hàng thuộc cơ sở của bạn — chỉ xem, hệ thống tự tạo hóa đơn
          theo từng flow (đặt cọc, gia hạn, trả kho, sự cố).
        </p>
      </div>

      <div className="mb-8 grid grid-cols-2 gap-4 max-[520px]:grid-cols-1">
        <div className="border border-border bg-surface p-5">
          <p className="m-0 flex items-center gap-2 font-mono text-[10px] font-bold uppercase tracking-[0.08em] text-warning">
            <Wallet size={13} /> Tổng công nợ chưa thu
          </p>
          <p className="m-0 mt-2 text-[22px] font-bold text-ink">{formatPrice(totalUnpaid)}đ</p>
        </div>
        <div className="border border-border bg-surface p-5">
          <p className="m-0 flex items-center gap-2 font-mono text-[10px] font-bold uppercase tracking-[0.08em] text-success">
            <CircleDollarSign size={13} /> Tổng đã thu
          </p>
          <p className="m-0 mt-2 text-[22px] font-bold text-ink">{formatPrice(totalPaid)}đ</p>
        </div>
      </div>

      <div className="mb-6 flex flex-wrap items-center gap-3">
        <select
          className={FILTER_SELECT}
          value={typeFilter}
          onChange={(event) => setTypeFilter(event.target.value as TypeFilter)}
        >
          <option value="all">Tất cả loại hóa đơn</option>
          {(Object.keys(TYPE_LABEL) as InvoiceType[]).map((type) => (
            <option key={type} value={type}>
              {TYPE_LABEL[type]}
            </option>
          ))}
        </select>
        <select
          className={FILTER_SELECT}
          value={statusFilter}
          onChange={(event) => setStatusFilter(event.target.value as StatusFilter)}
        >
          <option value="all">Tất cả trạng thái</option>
          {(Object.keys(STATUS_LABEL) as InvoiceStatus[]).map((status) => (
            <option key={status} value={status}>
              {STATUS_LABEL[status]}
            </option>
          ))}
        </select>
      </div>

      {list.length === 0 ? (
        <SurfaceState variant="empty" title="Không có hóa đơn nào phù hợp bộ lọc" />
      ) : (
        <div className="grid gap-3">
          {list.map((invoice) => (
            <button
              key={invoice.id}
              className="flex items-start justify-between gap-5 border border-border bg-surface p-5 text-left transition-colors hover:border-brand max-[760px]:flex-col"
              onClick={() => setOpenId(invoice.id)}
            >
              <div>
                <div className="flex items-center gap-2.5">
                  <span className="rounded-full bg-brand-soft px-2 py-0.5 text-[10px] font-bold text-brand">
                    {TYPE_LABEL[invoice.type]}
                  </span>
                  <p className="m-0 font-mono text-[13px] font-bold text-ink">{invoice.code}</p>
                </div>
                <p className="mb-0 mt-1.5 flex items-center gap-1.5 text-[13px] text-muted">
                  <UserRound size={13} /> {invoice.customerName} · Khoang {invoice.unitCode}
                </p>
                <p className="mb-0 mt-1 font-mono text-[10px] uppercase tracking-[0.06em] text-muted">
                  Hạn thanh toán {invoice.dueDate}
                </p>
              </div>
              <div className="flex flex-col items-end gap-2">
                <span className="text-[14px] font-bold text-ink">
                  {formatPrice(invoice.amount)}đ
                </span>
                <span
                  className={`whitespace-nowrap rounded-full px-[9px] py-[6px] text-[11px] font-extrabold ${STATUS_BADGE[invoice.status]}`}
                >
                  {STATUS_LABEL[invoice.status]}
                </span>
              </div>
            </button>
          ))}
        </div>
      )}

      {openInvoice && (
        <DetailPanel
          title={openInvoice.code}
          eyebrow={TYPE_LABEL[openInvoice.type]}
          onClose={() => setOpenId(null)}
        >
          <div className="grid gap-2.5 text-[13px] text-muted">
            <span className="flex items-center gap-2">
              <UserRound size={14} /> {openInvoice.customerName}
            </span>
            <span>Khoang: {openInvoice.unitCode}</span>
            <span>Hạn thanh toán: {openInvoice.dueDate}</span>
          </div>
          <div className="mt-4 grid gap-2 border border-border bg-surface-subtle p-3.5 text-[12px]">
            <div className="flex items-center justify-between">
              <span className="text-muted">Số tiền</span>
              <strong className="text-[15px]">{formatPrice(openInvoice.amount)}đ</strong>
            </div>
            <div className="flex items-center justify-between border-t border-border pt-2">
              <span className="text-muted">Trạng thái</span>
              <span
                className={`rounded-full px-2 py-0.5 text-[11px] font-bold ${STATUS_BADGE[openInvoice.status]}`}
              >
                {STATUS_LABEL[openInvoice.status]}
              </span>
            </div>
          </div>
        </DetailPanel>
      )}
    </main>
  );
}
