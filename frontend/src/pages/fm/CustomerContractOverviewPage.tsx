import { ArrowRight, CalendarClock, LifeBuoy, UserRound, Wallet } from "lucide-react";
import { useState } from "react";
import type { Navigate } from "../../app/types";
import { fmContracts, type FmContract } from "../../mocks/fm";
import { DemoNotice } from "../../components/ui/DemoNotice";
import { DetailPanel } from "../../components/ui/DetailPanel";
import { SurfaceState } from "../../components/ui/SurfaceState";

type LifecycleStatus = "Active" | "ExpiringSoon" | "Overdue";
type StatusFilter = "all" | LifecycleStatus;

const LIFECYCLE_LABEL: Record<LifecycleStatus, string> = {
  Active: "Còn hiệu lực",
  ExpiringSoon: "Sắp hết hạn",
  Overdue: "Quá hạn",
};

const LIFECYCLE_BADGE: Record<LifecycleStatus, string> = {
  Active: "bg-success/14 text-success",
  ExpiringSoon: "bg-warning/14 text-warning",
  Overdue: "bg-danger/14 text-danger",
};

const PAYMENT_LABEL: Record<FmContract["paymentStatus"], string> = {
  Paid: "Đã thanh toán",
  Overdue: "Chưa thanh toán",
};

const SECONDARY_BUTTON =
  "inline-flex min-h-10 items-center justify-center gap-2 rounded-sm border border-brand bg-transparent px-4 text-[12.5px] font-[750] text-brand transition-colors duration-[180ms] ease hover:bg-brand hover:text-background";
const FILTER_SELECT =
  "min-w-[170px] rounded-sm border border-border bg-surface px-[11px] py-[10px] pr-[30px] text-[12px] font-semibold text-ink";

const EXPIRING_SOON_DAYS = 14;

function parseVnDate(value: string) {
  const [day, month, year] = value.split("/").map(Number);
  return new Date(year, month - 1, day);
}

function lifecycleStatus(endDate: string): LifecycleStatus {
  const diffDays = Math.round(
    (parseVnDate(endDate).getTime() - Date.now()) / (1000 * 60 * 60 * 24),
  );
  if (diffDays < 0) return "Overdue";
  if (diffDays <= EXPIRING_SOON_DAYS) return "ExpiringSoon";
  return "Active";
}

export function CustomerContractOverviewPage({ navigate }: { navigate: Navigate }) {
  const [statusFilter, setStatusFilter] = useState<StatusFilter>("all");
  const [openId, setOpenId] = useState<string | null>(null);

  const withStatus = fmContracts
    .map((contract) => ({ ...contract, lifecycle: lifecycleStatus(contract.endDate) }))
    .sort((a, b) => parseVnDate(a.endDate).getTime() - parseVnDate(b.endDate).getTime());

  const list = withStatus.filter(
    (contract) => statusFilter === "all" || contract.lifecycle === statusFilter,
  );
  const openContract = withStatus.find((contract) => contract.id === openId) ?? null;

  return (
    <main>
      <div className="mb-8">
        <h1 className="m-0 text-[28px] tracking-[-0.03em] text-ink">
          Theo dõi khách hàng &amp; hợp đồng
        </h1>
        <p className="mt-2 text-[13px] text-muted">
          Toàn bộ hợp đồng đang hiệu lực tại cơ sở của bạn — chỉ xem, các thao tác duyệt thực hiện ở
          từng hàng chờ riêng.
        </p>
      </div>

      <div className="mb-6 flex flex-wrap items-center gap-3">
        <select
          className={FILTER_SELECT}
          value={statusFilter}
          onChange={(event) => setStatusFilter(event.target.value as StatusFilter)}
        >
          <option value="all">Tất cả trạng thái</option>
          <option value="Active">Còn hiệu lực</option>
          <option value="ExpiringSoon">Sắp hết hạn</option>
          <option value="Overdue">Quá hạn</option>
        </select>
      </div>

      {list.length === 0 ? (
        <SurfaceState variant="empty" title="Không có hợp đồng nào phù hợp bộ lọc" />
      ) : (
        <div className="grid gap-3">
          {list.map((contract) => (
            <button
              key={contract.id}
              className="flex items-start justify-between gap-5 border border-border bg-surface p-5 text-left transition-colors hover:border-brand max-[760px]:flex-col"
              onClick={() => setOpenId(contract.id)}
            >
              <div>
                <p className="m-0 flex items-center gap-2 text-[15px] font-bold text-ink">
                  <UserRound size={15} /> {contract.customerName}
                </p>
                <p className="mb-0 mt-1.5 text-[13px] text-muted">
                  Khoang {contract.unitCode} · {contract.startDate} → {contract.endDate}
                </p>
                {(contract.hasOpenExtendRequest ||
                  contract.hasOpenReturnRequest ||
                  contract.hasOpenSupportRequest) && (
                  <p className="mb-0 mt-1.5 flex items-center gap-1.5 text-[11px] font-bold text-brand">
                    <ArrowRight size={12} /> Đang có yêu cầu mở liên quan
                  </p>
                )}
              </div>
              <div className="flex flex-col items-end gap-2">
                <span
                  className={`whitespace-nowrap rounded-full px-[9px] py-[6px] text-[11px] font-extrabold ${LIFECYCLE_BADGE[contract.lifecycle]}`}
                >
                  {LIFECYCLE_LABEL[contract.lifecycle]}
                </span>
                <span className="flex items-center gap-1.5 text-[11px] font-bold text-muted">
                  <Wallet size={12} /> {PAYMENT_LABEL[contract.paymentStatus]}
                </span>
              </div>
            </button>
          ))}
        </div>
      )}

      {openContract && (
        <DetailPanel
          title={openContract.customerName}
          eyebrow={`Hợp đồng · Khoang ${openContract.unitCode}`}
          onClose={() => setOpenId(null)}
        >
          <div className="grid gap-2.5 text-[13px] text-muted">
            <span>
              Thời hạn: {openContract.startDate} → {openContract.endDate}
            </span>
            <span className="flex items-center gap-2">
              Trạng thái:{" "}
              <span
                className={`rounded-full px-2 py-0.5 text-[11px] font-bold ${LIFECYCLE_BADGE[openContract.lifecycle]}`}
              >
                {LIFECYCLE_LABEL[openContract.lifecycle]}
              </span>
            </span>
            <span>Thanh toán: {PAYMENT_LABEL[openContract.paymentStatus]}</span>
          </div>

          <div className="mt-6 grid gap-2.5">
            <button className={SECONDARY_BUTTON} onClick={() => navigate("/fm/invoices")}>
              <Wallet size={14} /> Xem hoá đơn liên quan
            </button>
            {openContract.hasOpenExtendRequest && (
              <button className={SECONDARY_BUTTON} onClick={() => navigate("/fm/extend-requests")}>
                <CalendarClock size={14} /> Có yêu cầu gia hạn đang mở — xem hàng chờ
              </button>
            )}
            {openContract.hasOpenReturnRequest && (
              <button className={SECONDARY_BUTTON} onClick={() => navigate("/fm/return-requests")}>
                <ArrowRight size={14} /> Có yêu cầu trả kho đang mở — xem hàng chờ
              </button>
            )}
            {openContract.hasOpenSupportRequest && (
              <button className={SECONDARY_BUTTON} onClick={() => navigate("/fm/support-requests")}>
                <LifeBuoy size={14} /> Có yêu cầu hỗ trợ đang mở — xem hàng chờ
              </button>
            )}
          </div>

          {openContract.lifecycle === "Overdue" && (
            <div className="mt-6">
              <DemoNotice tone="error">
                Hợp đồng đã quá hạn hết hiệu lực — theo dõi để nhắc khách trả kho hoặc gia hạn.
              </DemoNotice>
            </div>
          )}
        </DetailPanel>
      )}
    </main>
  );
}
