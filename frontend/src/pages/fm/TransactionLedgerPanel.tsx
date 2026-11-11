import { ArrowDownLeft, ArrowUpRight, ReceiptText, UserRound } from "lucide-react";
import { useEffect, useState } from "react";
import { ApiError } from "../../app/api";
import {
  loadFacilityTransactions,
  type FmInvoiceRefundStatus,
  type FmTransaction,
  type FmTransactionDirection,
  type FmTransactionKind,
  type FmTransactionPage,
  type FmTransactionStatus,
} from "../../app/fmTransactions";
import { Button } from "../../components/ui/Button";
import { DetailPanel } from "../../components/ui/DetailPanel";
import { FilterSelect } from "../../components/ui/FilterSelect";
import { INPUT_ELEMENT, INPUT_WRAP } from "../../components/ui/FormField";
import { StatusTag } from "../../components/ui/StatusTag";
import { SurfaceState } from "../../components/ui/SurfaceState";
import { formatPrice } from "../../mocks/catalog";

type KindFilter = "all" | FmTransactionKind;
type DirectionFilter = "all" | FmTransactionDirection;
type StatusFilter = "all" | FmTransactionStatus;
type RefundFilter = "all" | FmInvoiceRefundStatus;
type ReconciliationFilter = "all" | "stale";

const PAGE_SIZE = 20;

const KIND_LABEL: Record<FmTransactionKind, string> = {
  INVOICE: "Hoá đơn",
  FEE: "Phí phát sinh",
  PAYMENT: "Thanh toán",
  REFUND: "Hoàn tiền",
};

const STATUS_LABEL: Record<FmTransactionStatus, string> = {
  Unpaid: "Chưa thanh toán",
  Paid: "Đã thanh toán",
  Canceled: "Đã huỷ",
  Expired: "Quá hạn",
  Pending: "Đang chờ",
  Failed: "Thất bại",
  Success: "Thành công",
  Duplicate: "Trùng giao dịch",
};

const STATUS_TONE: Record<FmTransactionStatus, "warning" | "success" | "neutral" | "danger"> = {
  Unpaid: "warning",
  Paid: "success",
  Canceled: "neutral",
  Expired: "danger",
  Pending: "warning",
  Failed: "danger",
  Success: "success",
  Duplicate: "danger",
};

const REFUND_LABEL: Record<FmInvoiceRefundStatus, string> = {
  None: "Không hoàn",
  Required: "Cần hoàn tiền",
  Processing: "Đang xử lý hoàn",
  AwaitingConfirmation: "Chờ khách xác nhận",
  Refunded: "Đã hoàn tiền",
};

const REFUND_TONE: Record<
  Exclude<FmInvoiceRefundStatus, "None">,
  "warning" | "success" | "danger"
> = {
  Required: "danger",
  Processing: "warning",
  AwaitingConfirmation: "warning",
  Refunded: "success",
};

function formatDateTime(value: string | null) {
  if (!value) return "—";
  return new Intl.DateTimeFormat("vi-VN", {
    dateStyle: "short",
    timeStyle: "short",
  }).format(new Date(value));
}

function startOfDayIso(value: string) {
  return new Date(`${value}T00:00:00`).toISOString();
}

function nextDayIso(value: string) {
  const date = new Date(`${value}T00:00:00`);
  date.setDate(date.getDate() + 1);
  return date.toISOString();
}

function directionLabel(transaction: FmTransaction) {
  if (transaction.kind === "INVOICE" || transaction.kind === "FEE") return "Phát sinh";
  return transaction.direction === "IN" ? "Tiền vào" : "Tiền ra";
}

function amountTone(transaction: FmTransaction) {
  if (transaction.kind === "REFUND") return "text-danger";
  if (transaction.kind === "PAYMENT" && transaction.status === "Success") return "text-success";
  return "text-ink";
}

function amountSign(transaction: FmTransaction) {
  return transaction.direction === "OUT" ? "−" : "+";
}

function rowKey(transaction: FmTransaction) {
  return `${transaction.kind}-${transaction.id}`;
}

function refundReasonLabel(reason: string | null) {
  if (reason === "UNIT_UNAVAILABLE") return "Khoang không còn khả dụng";
  if (reason === "DUPLICATE_PAYMENT") return "Thanh toán trùng";
  if (reason === "DEPOSIT_SURPLUS") return "Cọc dư";
  if (reason === "OTHER") return "Khác";
  return reason ?? "—";
}

export function TransactionLedgerPanel() {
  const [kindFilter, setKindFilter] = useState<KindFilter>("all");
  const [directionFilter, setDirectionFilter] = useState<DirectionFilter>("all");
  const [statusFilter, setStatusFilter] = useState<StatusFilter>("all");
  const [refundFilter, setRefundFilter] = useState<RefundFilter>("all");
  const [reconciliationFilter, setReconciliationFilter] = useState<ReconciliationFilter>("all");
  const [fromDate, setFromDate] = useState("");
  const [toDate, setToDate] = useState("");
  const [page, setPage] = useState(0);
  const [openKey, setOpenKey] = useState<string | null>(null);
  const [data, setData] = useState<FmTransactionPage>({
    items: [],
    page: 0,
    size: PAGE_SIZE,
    totalElements: 0,
    totalPages: 0,
  });
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    loadFacilityTransactions(
      {
        kind: kindFilter === "all" ? undefined : kindFilter,
        direction: directionFilter === "all" ? undefined : directionFilter,
        status: statusFilter === "all" ? undefined : statusFilter,
        refundStatus: refundFilter === "all" ? undefined : refundFilter,
        needsReconciliation: reconciliationFilter === "stale" ? true : undefined,
        from: fromDate ? startOfDayIso(fromDate) : undefined,
        to: toDate ? nextDayIso(toDate) : undefined,
        page,
        size: PAGE_SIZE,
      },
      controller.signal,
    )
      .then((result) => {
        if (!controller.signal.aborted) {
          setData(result);
          setOpenKey(null);
        }
      })
      .catch((reason: unknown) => {
        if (controller.signal.aborted) return;
        setError(
          reason instanceof ApiError || reason instanceof Error
            ? reason.message
            : "Không tải được lịch sử giao dịch.",
        );
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [kindFilter, directionFilter, statusFilter, refundFilter, fromDate, toDate, page, reloadKey]);

  function reload() {
    setLoading(true);
    setError(null);
    setReloadKey((key) => key + 1);
  }

  function resetPage() {
    setPage(0);
    setLoading(true);
    setError(null);
  }

  function changeKind(value: KindFilter) {
    setKindFilter(value);
    resetPage();
  }

  function changeDirection(value: DirectionFilter) {
    setDirectionFilter(value);
    resetPage();
  }

  function changeStatus(value: StatusFilter) {
    setStatusFilter(value);
    resetPage();
  }

  function changeRefund(value: RefundFilter) {
    setRefundFilter(value);
    resetPage();
  }

  function changeReconciliation(value: ReconciliationFilter) {
    setReconciliationFilter(value);
    resetPage();
  }

  function changeFrom(value: string) {
    setFromDate(value);
    resetPage();
  }

  function changeTo(value: string) {
    setToDate(value);
    resetPage();
  }

  function changePage(next: number) {
    setPage(next);
    setLoading(true);
    setError(null);
  }

  const transactions = data.items;
  const openTransaction = transactions.find((item) => rowKey(item) === openKey) ?? null;

  return (
    <div className="grid gap-6">
      <div className="grid grid-cols-3 items-end gap-3 max-[900px]:grid-cols-1">
        <FilterSelect
          label="Loại giao dịch"
          value={kindFilter}
          onChange={(value) => changeKind(value as KindFilter)}
        >
          <option value="all">Tất cả loại</option>
          {(Object.keys(KIND_LABEL) as FmTransactionKind[]).map((kind) => (
            <option key={kind} value={kind}>
              {KIND_LABEL[kind]}
            </option>
          ))}
        </FilterSelect>
        <FilterSelect
          label="Chiều dòng tiền"
          value={directionFilter}
          onChange={(value) => changeDirection(value as DirectionFilter)}
        >
          <option value="all">Cả hai chiều</option>
          <option value="IN">Tiền vào / phát sinh</option>
          <option value="OUT">Tiền ra</option>
        </FilterSelect>
        <FilterSelect
          label="Trạng thái"
          value={statusFilter}
          onChange={(value) => changeStatus(value as StatusFilter)}
        >
          <option value="all">Tất cả trạng thái</option>
          <optgroup label="Hoá đơn">
            <option value="Unpaid">Chưa thanh toán</option>
            <option value="Paid">Đã thanh toán</option>
            <option value="Canceled">Đã huỷ</option>
            <option value="Expired">Quá hạn</option>
          </optgroup>
          <optgroup label="Giao dịch">
            <option value="Pending">Đang chờ</option>
            <option value="Success">Thành công</option>
            <option value="Failed">Thất bại</option>
            <option value="Duplicate">Trùng giao dịch</option>
          </optgroup>
        </FilterSelect>
        <FilterSelect
          label="Hoàn tiền của hoá đơn"
          value={refundFilter}
          onChange={(value) => changeRefund(value as RefundFilter)}
        >
          <option value="all">Tất cả</option>
          <option value="Required">Cần hoàn tiền</option>
          <option value="Processing">Đang xử lý hoàn</option>
          <option value="AwaitingConfirmation">Chờ khách xác nhận</option>
          <option value="Refunded">Đã hoàn tiền</option>
          <option value="None">Không hoàn</option>
        </FilterSelect>
        <FilterSelect
          label="Đối soát"
          value={reconciliationFilter}
          onChange={(value) => changeReconciliation(value as ReconciliationFilter)}
        >
          <option value="all">Tất cả giao dịch</option>
          <option value="stale">Pending quá 15 phút</option>
        </FilterSelect>
        <label className="grid gap-1.5">
          <span className="text-label text-muted">Từ ngày</span>
          <div className={INPUT_WRAP}>
            <input
              type="date"
              className={INPUT_ELEMENT}
              value={fromDate}
              onChange={(event) => changeFrom(event.target.value)}
            />
          </div>
        </label>
        <label className="grid gap-1.5">
          <span className="text-label text-muted">Đến ngày</span>
          <div className={INPUT_WRAP}>
            <input
              type="date"
              className={INPUT_ELEMENT}
              value={toDate}
              onChange={(event) => changeTo(event.target.value)}
            />
          </div>
        </label>
      </div>

      {loading ? (
        <SurfaceState variant="loading" title="Đang tải lịch sử giao dịch" />
      ) : error ? (
        <SurfaceState
          variant="error"
          title="Không tải được lịch sử giao dịch"
          description={error}
          action={{ label: "Thử lại", onClick: reload }}
        />
      ) : transactions.length === 0 ? (
        <SurfaceState variant="empty" title="Không có giao dịch nào phù hợp bộ lọc" />
      ) : (
        <div className="grid gap-2">
          {transactions.map((transaction) => (
            <button
              key={rowKey(transaction)}
              className={`grid grid-cols-[minmax(0,1.5fr)_minmax(0,0.8fr)_minmax(0,0.7fr)_auto] items-center gap-4 bg-surface p-4 text-left transition-colors hover:bg-surface-subtle max-[900px]:grid-cols-1 max-[900px]:gap-2 ${
                transaction.needsReconciliation ? "border-l-4 border-danger" : ""
              }`}
              onClick={() => setOpenKey(rowKey(transaction))}
            >
              <div>
                <span className="flex flex-wrap items-center gap-2 text-label text-muted">
                  {KIND_LABEL[transaction.kind]} · {transaction.code}
                  {transaction.invoiceRefundStatus &&
                    transaction.invoiceRefundStatus !== "None" && (
                      <StatusTag tone={REFUND_TONE[transaction.invoiceRefundStatus]}>
                        {REFUND_LABEL[transaction.invoiceRefundStatus]}
                      </StatusTag>
                    )}
                  {transaction.refundDisputedAt && (
                    <StatusTag tone="danger">Khách báo chưa nhận</StatusTag>
                  )}
                  {transaction.needsReconciliation && (
                    <StatusTag tone="danger">Cần đối soát</StatusTag>
                  )}
                </span>
                <strong className="mt-1 block text-small font-semibold text-ink">
                  {transaction.description}
                </strong>
                <span className="mt-1 flex items-center gap-1.5 text-label text-muted">
                  <UserRound size={12} /> {transaction.customerName ?? transaction.customerEmail} ·
                  Khoang {transaction.unitCode ?? "—"}
                </span>
              </div>
              <span className="flex items-center gap-1.5 text-label text-muted">
                {transaction.direction === "OUT" ? (
                  <ArrowUpRight size={13} />
                ) : (
                  <ArrowDownLeft size={13} />
                )}
                {directionLabel(transaction)} · {formatDateTime(transaction.occurredAt)}
              </span>
              <strong className={`text-[18px] font-semibold ${amountTone(transaction)}`}>
                {amountSign(transaction)}
                {formatPrice(transaction.amount)} đ
              </strong>
              <div className="grid justify-items-end gap-1">
                <StatusTag tone={STATUS_TONE[transaction.status]}>
                  {STATUS_LABEL[transaction.status]}
                </StatusTag>
                {transaction.method && (
                  <span className="text-label text-muted">{transaction.method}</span>
                )}
              </div>
            </button>
          ))}
        </div>
      )}

      <div className="flex flex-wrap items-center justify-between gap-3">
        <span className="text-label text-muted">
          Trang {Math.min(page + 1, Math.max(1, data.totalPages))} / {Math.max(1, data.totalPages)}{" "}
          · {data.totalElements} giao dịch
        </span>
        <div className="flex gap-2">
          <Button
            variant="secondary"
            disabled={loading || page === 0}
            onClick={() => changePage(page - 1)}
          >
            Trang trước
          </Button>
          <Button
            variant="secondary"
            disabled={loading || page + 1 >= data.totalPages}
            onClick={() => changePage(page + 1)}
          >
            Trang sau
          </Button>
        </div>
      </div>

      {openTransaction && (
        <DetailPanel
          title={openTransaction.code}
          eyebrow={KIND_LABEL[openTransaction.kind]}
          onClose={() => setOpenKey(null)}
        >
          <div className="grid gap-2 text-small text-muted">
            <span className="flex items-center gap-2">
              <ReceiptText size={14} /> {openTransaction.invoiceCode ?? "—"}
            </span>
            <span className="flex items-center gap-2">
              <UserRound size={14} />{" "}
              {openTransaction.customerName ?? openTransaction.customerEmail}
            </span>
            <span>Khoang: {openTransaction.unitCode ?? "—"}</span>
            <span>Đơn thuê: {openTransaction.orderId ? `#${openTransaction.orderId}` : "—"}</span>
            <span>Thời điểm: {formatDateTime(openTransaction.occurredAt)}</span>
            <span>Phương thức: {openTransaction.method ?? "—"}</span>
            {openTransaction.kind === "PAYMENT" && openTransaction.status === "Pending" && (
              <span>Đã chờ: {openTransaction.pendingAgeMinutes} phút</span>
            )}
            {openTransaction.needsReconciliation && (
              <span className="text-danger">
                Pending quá 15 phút — ADMIN tra portal VNPay và chốt thủ công.
              </span>
            )}
            {openTransaction.paymentTransactionId && (
              <span>Payment #{openTransaction.paymentTransactionId}</span>
            )}
            {openTransaction.refundTransactionId && (
              <span>Refund #{openTransaction.refundTransactionId}</span>
            )}
          </div>

          <div className="mt-4 grid gap-2 bg-surface-subtle p-3.5 text-small">
            <div className="flex items-center justify-between gap-4">
              <span className="text-muted">Số tiền</span>
              <strong className={`text-[18px] font-semibold ${amountTone(openTransaction)}`}>
                {amountSign(openTransaction)}
                {formatPrice(openTransaction.amount)} đ
              </strong>
            </div>
            <div className="flex items-center justify-between gap-4">
              <span className="text-muted">Trạng thái</span>
              <StatusTag tone={STATUS_TONE[openTransaction.status]}>
                {STATUS_LABEL[openTransaction.status]}
              </StatusTag>
            </div>
            {openTransaction.invoiceRefundStatus &&
              openTransaction.invoiceRefundStatus !== "None" && (
                <div className="flex items-center justify-between gap-4">
                  <span className="text-muted">Hoàn tiền</span>
                  <StatusTag tone={REFUND_TONE[openTransaction.invoiceRefundStatus]}>
                    {REFUND_LABEL[openTransaction.invoiceRefundStatus]}
                  </StatusTag>
                </div>
              )}
          </div>

          {openTransaction.kind === "INVOICE" || openTransaction.kind === "FEE" ? (
            openTransaction.invoiceRefundReason && (
              <p className="mt-3 text-small text-muted">
                Lý do hoàn tiền: {refundReasonLabel(openTransaction.invoiceRefundReason)}
              </p>
            )
          ) : openTransaction.kind === "REFUND" ? (
            <div className="mt-3 grid gap-1.5 text-small text-muted">
              <span>Khách xác nhận: {formatDateTime(openTransaction.refundConfirmedAt)}</span>
              <span>Khách báo chưa nhận: {formatDateTime(openTransaction.refundDisputedAt)}</span>
              {openTransaction.refundDisputeNote && (
                <span className="text-danger">Ghi chú: {openTransaction.refundDisputeNote}</span>
              )}
            </div>
          ) : null}
        </DetailPanel>
      )}
    </div>
  );
}
