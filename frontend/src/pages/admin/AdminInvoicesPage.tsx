import { ReceiptText } from "lucide-react";
import { useEffect, useState } from "react";
import { ApiError } from "../../app/api";
import {
  loadAdminInvoices,
  type AdminInvoice,
  type AdminInvoicePage,
} from "../../app/adminInvoices";
import { formatPrice } from "../../mocks/catalog";
import { StatusTag } from "../../components/ui/StatusTag";
import { SurfaceState } from "../../components/ui/SurfaceState";
import { Button } from "../../components/ui/Button";
import { RefundRequiredPanel } from "./RefundRequiredPanel";

const EMPTY_PAGE: AdminInvoicePage = {
  items: [],
  page: 0,
  size: 20,
  totalElements: 0,
  totalPages: 0,
};

const STATUS_LABEL: Record<AdminInvoice["status"], string> = {
  Unpaid: "Chưa thanh toán",
  Paid: "Đã thanh toán",
  Canceled: "Đã huỷ",
  Expired: "Hết hạn",
};

function formatDateTime(value: string | null) {
  if (!value) return "—";
  return new Intl.DateTimeFormat("vi-VN", { dateStyle: "short" }).format(new Date(value));
}

export function AdminInvoicesPage() {
  const [tab, setTab] = useState<"invoices" | "refunds">("invoices");
  const [page, setPage] = useState(0);
  const [data, setData] = useState<AdminInvoicePage>(EMPTY_PAGE);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    if (tab !== "invoices") return;
    let active = true;
    setLoading(true);
    setError(null);
    loadAdminInvoices(page)
      .then((result) => {
        if (active) setData(result);
      })
      .catch((reason: unknown) => {
        if (!active) return;
        setError(
          reason instanceof ApiError || reason instanceof Error
            ? reason.message
            : "Không tải được danh sách hoá đơn.",
        );
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [page, reloadKey, tab]);

  function changeTab(next: "invoices" | "refunds") {
    setTab(next);
    setPage(0);
  }

  return (
    <div className="grid gap-6">
      <div>
        <h1 className="m-0 text-title text-ink">Hoá đơn</h1>
        <p className="mt-1.5 text-small text-muted">
          Danh sách hoá đơn toàn hệ thống và các khoản đang cần hoàn tiền.
        </p>
      </div>

      <div className="flex gap-2 border-b border-rule" role="tablist" aria-label="Hoá đơn">
        <button
          type="button"
          role="tab"
          aria-selected={tab === "invoices"}
          className={`border-b-2 px-3 py-2 text-small font-semibold ${tab === "invoices" ? "border-accent text-accent" : "border-transparent text-muted hover:text-ink"}`}
          onClick={() => changeTab("invoices")}
        >
          Hoá đơn
        </button>
        <button
          type="button"
          role="tab"
          aria-selected={tab === "refunds"}
          className={`border-b-2 px-3 py-2 text-small font-semibold ${tab === "refunds" ? "border-accent text-accent" : "border-transparent text-muted hover:text-ink"}`}
          onClick={() => changeTab("refunds")}
        >
          Hoàn tiền
        </button>
      </div>

      {tab === "refunds" ? (
        <RefundRequiredPanel />
      ) : loading ? (
        <SurfaceState variant="loading" title="Đang tải danh sách hoá đơn" />
      ) : error ? (
        <SurfaceState
          variant="error"
          title="Không tải được danh sách hoá đơn"
          description={error}
          action={{
            label: "Thử lại",
            onClick: () => {
              setLoading(true);
              setReloadKey((key) => key + 1);
            },
          }}
        />
      ) : data.items.length === 0 ? (
        <SurfaceState variant="empty" title="Chưa có hoá đơn nào" />
      ) : (
        <div className="grid gap-2">
          {data.items.map((invoice) => (
            <div
              key={invoice.id}
              className="grid grid-cols-[minmax(0,1.6fr)_minmax(0,0.8fr)_auto] items-center gap-4 bg-surface p-4 max-[760px]:grid-cols-1 max-[760px]:gap-2"
            >
              <div>
                <span className="flex items-center gap-2 text-label text-muted">
                  <ReceiptText size={14} /> {invoice.code} · {invoice.type}
                </span>
                <strong className="mt-1 block text-small font-semibold text-ink">
                  {invoice.title}
                </strong>
                <span className="mt-1 block text-label text-muted">
                  {invoice.unitCode ?? "—"} · {invoice.facilityName ?? "—"}
                </span>
              </div>
              <div className="text-small text-muted">
                <span className="block text-label">Hạn</span>
                <span className="text-ink">{formatDateTime(invoice.dueDate)}</span>
              </div>
              <div className="flex items-center justify-end gap-3">
                <strong className="text-[18px] font-semibold text-ink">
                  {formatPrice(invoice.amount)} đ
                </strong>
                <StatusTag tone={invoice.status === "Paid" ? "success" : "warning"}>
                  {STATUS_LABEL[invoice.status]}
                </StatusTag>
              </div>
            </div>
          ))}
        </div>
      )}

      {tab === "invoices" && !loading && !error && data.totalPages > 1 && (
        <div className="flex flex-wrap items-center justify-between gap-3">
          <span className="text-label text-muted">
            Trang {Math.min(page + 1, data.totalPages)} / {data.totalPages} · {data.totalElements}{" "}
            hoá đơn
          </span>
          <div className="flex gap-2">
            <Button
              variant="secondary"
              disabled={page === 0}
              onClick={() => setPage((current) => current - 1)}
            >
              Trang trước
            </Button>
            <Button
              variant="secondary"
              disabled={page + 1 >= data.totalPages}
              onClick={() => setPage((current) => current + 1)}
            >
              Trang sau
            </Button>
          </div>
        </div>
      )}
    </div>
  );
}
