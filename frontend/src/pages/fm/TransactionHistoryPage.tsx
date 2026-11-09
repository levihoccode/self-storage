import { useState } from "react";
import { loadFmRefundCases } from "../../app/fmRefunds";
import { FmRefundQueuePanel } from "./FmRefundQueuePanel";
import { TransactionLedgerPanel } from "./TransactionLedgerPanel";

type Tab = "ledger" | "refunds";

export function TransactionHistoryPage() {
  const [tab, setTab] = useState<Tab>("ledger");

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
          className={`border-b-2 px-3 py-2 text-small font-semibold ${
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
          className={`border-b-2 px-3 py-2 text-small font-semibold ${
            tab === "refunds"
              ? "border-accent text-accent"
              : "border-transparent text-muted hover:text-ink"
          }`}
          onClick={() => setTab("refunds")}
        >
          Chờ hoàn tiền
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
