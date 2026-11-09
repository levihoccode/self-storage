import { useEffect, useState } from "react";
import { loadFmRefundCases } from "../../app/fmRefunds";
import { FmRefundQueuePanel } from "./FmRefundQueuePanel";
import { TransactionLedgerPanel } from "./TransactionLedgerPanel";

type Tab = "ledger" | "refunds";

export function TransactionHistoryPage() {
  const [tab, setTab] = useState<Tab>("ledger");
  const [refundCount, setRefundCount] = useState<number | null>(null);

  useEffect(() => {
    let active = true;
    loadFmRefundCases(0, 1)
      .then((page) => {
        if (active) setRefundCount(page.totalElements);
      })
      .catch(() => {
        if (active) setRefundCount(null);
      });
    return () => {
      active = false;
    };
  }, [tab]);

  return (
    <div className="grid gap-6">
      <div>
        <h1 className="m-0 text-title text-ink">Lịch sử giao dịch</h1>
        <p className="mt-1.5 text-small text-muted">
          Toàn bộ dòng tiền của cơ sở: hoá đơn và phí phát sinh, thanh toán khách trả, hoàn tiền
          theo ledger. Khoản chưa chi nằm ở tab Chờ hoàn tiền — nghĩa vụ, không phải giao dịch.
        </p>
      </div>

      <div
        className="flex gap-2 border-b border-rule"
        role="tablist"
        aria-label="Lịch sử giao dịch cơ sở"
      >
        <button
          type="button"
          role="tab"
          aria-selected={tab === "ledger"}
          className={`inline-flex items-center gap-2 border-b-2 px-3 py-2 text-small font-semibold ${
            tab === "ledger"
              ? "border-accent text-accent"
              : "border-transparent text-muted hover:text-ink"
          }`}
          onClick={() => setTab("ledger")}
        >
          Giao dịch
        </button>
        <button
          type="button"
          role="tab"
          aria-selected={tab === "refunds"}
          className={`inline-flex items-center gap-2 border-b-2 px-3 py-2 text-small font-semibold ${
            tab === "refunds"
              ? "border-accent text-accent"
              : "border-transparent text-muted hover:text-ink"
          }`}
          onClick={() => setTab("refunds")}
        >
          Chờ hoàn tiền
          {refundCount !== null && (
            <span
              className={`inline-flex min-w-[18px] items-center justify-center px-1 py-0.5 text-[11px] font-bold ${
                refundCount > 0 ? "bg-danger-soft text-danger" : "bg-surface-subtle text-muted"
              }`}
            >
              {refundCount}
            </span>
          )}
        </button>
      </div>

      {tab === "ledger" ? (
        <TransactionLedgerPanel />
      ) : (
        <FmRefundQueuePanel loadCases={loadFmRefundCases} />
      )}
    </div>
  );
}
