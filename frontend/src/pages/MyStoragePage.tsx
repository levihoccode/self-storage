import { ArrowRight, Plus } from "lucide-react";
import type { Navigate } from "../app/types";
import { formatPrice } from "../mocks/catalog";
import { rentedStorage, type StorageStatus } from "../mocks/customer";
import { proposals } from "../mocks/proposals";
import { Button } from "../components/ui/Button";

const STATUS: Record<StorageStatus, { label: string; className: string }> = {
  active: { label: "Đang thuê", className: "bg-success-soft text-success" },
  expiring: { label: "Sắp đến hạn", className: "bg-warning-soft text-warning" },
  overdue: { label: "Đã quá hạn", className: "bg-danger-soft text-danger" },
};

export function MyStoragePage({ navigate }: { navigate: Navigate }) {
  const pendingProposalCount = proposals.filter((proposal) => proposal.status === "pending").length;
  const expiring = rentedStorage.filter((storage) => storage.status !== "active");
  const rentalCount = rentedStorage.length;
  const firstExpiring = expiring[0];
  const taskCount = (pendingProposalCount > 0 ? 1 : 0) + (firstExpiring ? 1 : 0);

  return (
    <div className="grid gap-6">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="m-0 text-title text-ink">Kho của tôi</h1>
          <p className="mt-1.5 text-small text-muted">
            Theo dõi hợp đồng, thời hạn và việc cần làm tiếp theo.
          </p>
        </div>
        <Button onClick={() => navigate("/rental-requests/new")}>
          Thuê thêm kho <Plus size={16} />
        </Button>
      </div>

      <div className="grid grid-cols-3 gap-2 max-[760px]:grid-cols-1">
        <div className="bg-surface p-4">
          <strong className="block text-stat text-ink">
            {String(rentalCount).padStart(2, "0")}
          </strong>
          <span className="text-label text-muted">Khoang đang thuê</span>
        </div>
        <div className="bg-surface p-4">
          <strong className="block text-stat text-ink">
            {String(pendingProposalCount).padStart(2, "0")}
          </strong>
          <span className="text-label text-muted">Đề xuất chờ xác nhận</span>
        </div>
        <div className="bg-surface p-4">
          <strong className="block text-stat text-ink">
            {String(expiring.length).padStart(2, "0")}
          </strong>
          <span className="text-label text-muted">Khoang cần chú ý</span>
        </div>
      </div>

      <div className="grid grid-cols-[minmax(0,1fr)_312px] items-start gap-8 max-[1100px]:grid-cols-1">
        <section>
          <div className="mb-2 flex items-center justify-between gap-4">
            <h2 className="flex items-center gap-2 text-[15px] font-semibold text-ink">
              <span className="h-2 w-2 bg-brand" aria-hidden="true" />
              Hợp đồng đang hoạt động
            </h2>
            <span className="text-label text-muted">{rentedStorage.length} hợp đồng</span>
          </div>
          <div className="grid gap-2.5">
            {rentedStorage.map((storage) => {
              const status = STATUS[storage.status];
              return (
                <div
                  key={storage.id}
                  className="grid grid-cols-[84px_minmax(0,1.25fr)_minmax(0,0.85fr)_minmax(0,0.85fr)_auto] items-center gap-4 bg-surface p-4 max-[1100px]:grid-cols-[64px_minmax(0,1fr)_auto] max-[760px]:gap-2"
                >
                  <div className="min-w-0">
                    <strong className="block whitespace-nowrap text-stat text-ink">
                      {storage.unitCode}
                    </strong>
                    <span className="text-label text-muted">{storage.size}</span>
                  </div>
                  <div className="min-w-0 max-[1100px]:col-span-2">
                    <strong className="block truncate text-small font-semibold text-ink">
                      {storage.facility}
                    </strong>
                    <span className="mt-0.5 block truncate text-label text-muted">
                      {storage.district}
                    </span>
                  </div>
                  <div className="whitespace-nowrap text-label text-ink max-[1100px]:hidden">
                    <span className="block text-label text-muted">Thời hạn</span>
                    {storage.startDate} → {storage.endDate}
                  </div>
                  <div className="text-small text-ink max-[1100px]:hidden">
                    <span className="block text-label text-muted">Thanh toán</span>
                    {formatPrice(storage.monthlyPrice)} đ / tháng
                    <span className="mt-2 block h-[3px] w-full bg-border" aria-hidden="true">
                      <span
                        className="block h-full bg-brand"
                        style={{ width: storage.status === "active" ? "78%" : "92%" }}
                      />
                    </span>
                    <span className="mt-1 block text-label text-muted">{storage.paymentLabel}</span>
                  </div>
                  <div className="flex flex-col items-end gap-1.5 max-[1100px]:col-start-3 max-[1100px]:row-start-1">
                    <span
                      className={`inline-flex items-center gap-1.5 px-2 py-1 text-label font-semibold ${status.className}`}
                    >
                      <span className="h-1.5 w-1.5 rounded-full bg-current" aria-hidden="true" />
                      {status.label}
                    </span>
                    <Button
                      variant="secondary"
                      size="sm"
                      onClick={() => navigate(`/my-storage/${storage.id}`)}
                    >
                      Xem
                    </Button>
                  </div>
                </div>
              );
            })}
          </div>
        </section>

        <aside className="grid gap-4">
          <div className="bg-surface-subtle p-4">
            <div className="mb-3 flex items-center justify-between gap-3">
              <h2 className="flex items-center gap-2 text-small font-semibold text-ink">
                <span className="h-2 w-2 bg-brand" aria-hidden="true" />
                Việc cần làm
              </h2>
              <span className="text-label text-muted">{String(taskCount).padStart(2, "0")}</span>
            </div>
            <div className="grid gap-2">
              {pendingProposalCount > 0 && (
                <button
                  className="flex items-start justify-between gap-3 bg-surface p-3 text-left"
                  onClick={() => navigate("/proposals")}
                >
                  <span>
                    <strong className="block text-small font-semibold text-ink">
                      Xác nhận đề xuất khoang
                    </strong>
                    <span className="mt-0.5 block text-label text-muted">
                      {pendingProposalCount} đề xuất đang chờ phản hồi.
                    </span>
                  </span>
                  <ArrowRight size={16} className="mt-0.5 shrink-0 text-muted" />
                </button>
              )}
              {firstExpiring && (
                <button
                  className="flex items-start justify-between gap-3 bg-surface p-3 text-left"
                  onClick={() => navigate("/invoices")}
                >
                  <span>
                    <strong className="block text-small font-semibold text-ink">
                      Thanh toán khoang {firstExpiring.unitCode}
                    </strong>
                    <span className="mt-0.5 block text-label text-muted">
                      {firstExpiring.paymentLabel}
                    </span>
                  </span>
                  <ArrowRight size={16} className="mt-0.5 shrink-0 text-muted" />
                </button>
              )}
            </div>
          </div>

          <div className="bg-[var(--dark)] p-4 text-[var(--on-dark-ink)]">
            <h2 className="text-small font-semibold">Hỗ trợ nhanh</h2>
            <p className="mt-1.5 text-small text-[rgba(233,234,230,.7)]">
              Báo sự cố khoang, khoá truy cập hoặc yêu cầu hỗ trợ tại cơ sở.
            </p>
            <a
              className="mt-4 inline-flex min-h-[38px] items-center gap-2 border-2 border-on-dark-border px-3 text-small font-semibold text-on-dark-ink hover:border-on-dark-border-hover hover:bg-on-dark-bg-hover"
              href="mailto:hello@khomoc.example"
            >
              Gửi yêu cầu hỗ trợ <ArrowRight size={16} />
            </a>
          </div>
        </aside>
      </div>
    </div>
  );
}
