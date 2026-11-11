import { AlertTriangle, ArrowRight, Plus } from "lucide-react";
import { useEffect, useState } from "react";
import { listMyContracts } from "../app/contracts";
import { formatDate, formatMoney } from "../app/format";
import { fetchMyProposals } from "../app/proposals";
import type { CustomerContract, Navigate } from "../app/types";
import { Button } from "../components/ui/Button";
import { StatusTag } from "../components/ui/StatusTag";
import { SurfaceState } from "../components/ui/SurfaceState";

type LoadState = "loading" | "ready" | "error";

export function MyStoragePage({ navigate }: { navigate: Navigate }) {
  const [contracts, setContracts] = useState<CustomerContract[]>([]);
  const [pendingProposalCount, setPendingProposalCount] = useState(0);
  const [loadState, setLoadState] = useState<LoadState>("loading");
  const [loadError, setLoadError] = useState("");
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    let cancelled = false;

    async function load() {
      setLoadState("loading");
      setLoadError("");
      try {
        const next = await listMyContracts(controller.signal);
        if (cancelled) return;
        setContracts(next);
        setLoadState("ready");
      } catch (error) {
        if (cancelled || controller.signal.aborted) return;
        setLoadError(error instanceof Error ? error.message : "Không tải được hợp đồng.");
        setLoadState("error");
      }
      // Số đề xuất chờ duyệt là thông tin phụ — lỗi ở đây không làm hỏng trang.
      try {
        const pending = await fetchMyProposals("Pending");
        if (!cancelled) setPendingProposalCount(pending.length);
      } catch {
        if (!cancelled) setPendingProposalCount(0);
      }
    }

    void load();
    return () => {
      cancelled = true;
      controller.abort();
    };
  }, [reloadKey]);

  // Flow 3.1: nhóm theo cơ sở, giữ thứ tự hạn gần nhất của endpoint.
  const byFacility = contracts.reduce<{ id: number; name: string; items: CustomerContract[] }[]>(
    (groups, contract) => {
      const group = groups.find((item) => item.id === contract.facilityId);
      if (group) {
        group.items.push(contract);
      } else {
        groups.push({ id: contract.facilityId, name: contract.facilityName, items: [contract] });
      }
      return groups;
    },
    [],
  );

  const overdue = contracts.filter((contract) => contract.overdue);
  const expiringSoon = contracts.filter((contract) => contract.expiringSoon);
  const needsAttention = [...overdue, ...expiringSoon];
  const firstOverdue = overdue[0];
  const firstExpiring = expiringSoon[0];
  const taskCount = (pendingProposalCount > 0 ? 1 : 0) + (needsAttention.length > 0 ? 1 : 0);

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

      {loadState === "loading" && <SurfaceState variant="loading" title="Đang tải hợp đồng…" />}

      {loadState === "error" && (
        <SurfaceState
          variant="error"
          title="Không tải được hợp đồng"
          description={loadError}
          action={{ label: "Thử lại", onClick: () => setReloadKey((key) => key + 1) }}
        />
      )}

      {loadState === "ready" && contracts.length === 0 && (
        <SurfaceState
          variant="empty"
          title="Bạn chưa có hợp đồng nào đang hiệu lực"
          description="Gửi yêu cầu thuê kho để được đề xuất khoang phù hợp."
          action={{
            label: "Gửi yêu cầu thuê kho",
            onClick: () => navigate("/rental-requests/new"),
          }}
        />
      )}

      {loadState === "ready" && contracts.length > 0 && (
        <>
          <div className="grid grid-cols-3 gap-2 max-[760px]:grid-cols-1">
            <div className="bg-surface p-4">
              <strong className="block text-stat text-ink">
                {String(contracts.length).padStart(2, "0")}
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
                {String(needsAttention.length).padStart(2, "0")}
              </strong>
              <span className="text-label text-muted">Hợp đồng cần chú ý</span>
            </div>
          </div>

          <div className="grid grid-cols-[minmax(0,1fr)_312px] items-start gap-8 max-[1100px]:grid-cols-1">
            <section>
              <div className="mb-2 flex items-center justify-between gap-4">
                <h2 className="flex items-center gap-2 text-[15px] font-semibold text-ink">
                  <span className="h-2 w-2 bg-brand" aria-hidden="true" />
                  Hợp đồng đang hoạt động
                </h2>
                <span className="text-label text-muted">{contracts.length} hợp đồng</span>
              </div>
              <div className="grid gap-5">
                {byFacility.map((group) => (
                  <div key={group.id} className="grid gap-3">
                    <div className="flex items-center justify-between gap-3 border-b border-border pb-1.5">
                      <span className="text-small font-semibold text-ink">{group.name}</span>
                      <span className="text-label text-muted">{group.items.length} khoang</span>
                    </div>
                    {group.items.map((contract) => (
                      <article key={contract.id} className="border border-border bg-surface p-5">
                        <div className="flex items-start justify-between gap-4">
                          <div className="min-w-0">
                            <div className="flex items-baseline gap-2">
                              <strong className="whitespace-nowrap text-stat text-ink">
                                {contract.unitCode}
                              </strong>
                              <span className="text-label text-muted">
                                {contract.unitSize ?? contract.unitTypeName}
                              </span>
                            </div>
                            <strong className="mt-1 block truncate text-small font-semibold text-ink">
                              {contract.facilityName}
                            </strong>
                            <span className="mt-0.5 block truncate text-label text-muted">
                              {contract.facilityAddress ?? "—"}
                            </span>
                          </div>
                          <div className="flex shrink-0 flex-col items-end gap-2">
                            <StatusTag
                              tone={
                                contract.overdue
                                  ? "danger"
                                  : contract.expiringSoon
                                    ? "warning"
                                    : "success"
                              }
                            >
                              {contract.overdue
                                ? "Đã quá hạn"
                                : contract.expiringSoon
                                  ? "Sắp đến hạn"
                                  : "Đang thuê"}
                            </StatusTag>
                            <Button
                              variant="secondary"
                              size="sm"
                              onClick={() => navigate(`/my-storage/${contract.id}`)}
                            >
                              Xem
                            </Button>
                          </div>
                        </div>
                        <dl className="mt-4 grid grid-cols-2 gap-4 border-t border-border pt-4 max-[760px]:grid-cols-1">
                          <div>
                            <dt className="text-label text-muted">Thời hạn</dt>
                            <dd className="m-0 mt-1 whitespace-nowrap text-small text-ink">
                              {formatDate(contract.startDate)} →{" "}
                              {contract.endDate ? formatDate(contract.endDate) : "—"}
                            </dd>
                          </div>
                          <div>
                            <dt className="text-label text-muted">Giá thuê</dt>
                            <dd className="m-0 mt-1 text-small font-semibold text-ink">
                              {formatMoney(contract.monthlyPrice)} / tháng
                            </dd>
                          </div>
                        </dl>
                        {/* Chưa có dữ liệu hoá đơn trong `GET /api/contracts` — chỉ cảnh báo hạn hợp đồng. */}
                        {(contract.overdue || contract.expiringSoon) && (
                          <div
                            className={`mt-4 flex items-center justify-between gap-4 px-3 py-2.5 ${
                              contract.overdue
                                ? "bg-danger-soft text-danger"
                                : "bg-warning-soft text-warning"
                            }`}
                          >
                            <span className="flex items-center gap-2 text-small font-semibold">
                              <AlertTriangle size={16} />
                              {contract.overdue
                                ? "Hợp đồng đã quá hạn, liên hệ cơ sở để gia hạn"
                                : `Hợp đồng sắp hết hạn${
                                    contract.endDate ? ` ngày ${formatDate(contract.endDate)}` : ""
                                  }`}
                            </span>
                            <button
                              className="inline-flex shrink-0 items-center gap-1 border-0 bg-transparent p-0 text-label font-bold hover:underline"
                              onClick={() => navigate("/invoices")}
                            >
                              Hoá đơn <ArrowRight size={14} />
                            </button>
                          </div>
                        )}
                      </article>
                    ))}
                  </div>
                ))}
              </div>
            </section>

            <aside className="grid gap-4">
              <div className="bg-surface-subtle p-4">
                <div className="mb-3 flex items-center justify-between gap-3">
                  <h2 className="flex items-center gap-2 text-small font-semibold text-ink">
                    <span className="h-2 w-2 bg-brand" aria-hidden="true" />
                    Việc cần làm
                  </h2>
                  <span className="text-label text-muted">
                    {String(taskCount).padStart(2, "0")}
                  </span>
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
                  {(firstOverdue || firstExpiring) && (
                    <button
                      className="flex items-start justify-between gap-3 bg-surface p-3 text-left"
                      onClick={() => navigate("/invoices")}
                    >
                      <span>
                        <strong className="block text-small font-semibold text-ink">
                          Hợp đồng {(firstOverdue ?? firstExpiring)!.unitCode}{" "}
                          {firstOverdue ? "đã quá hạn" : "sắp hết hạn"}
                        </strong>
                        <span className="mt-0.5 block text-label text-muted">
                          Hạn hợp đồng{" "}
                          {(firstOverdue ?? firstExpiring)!.endDate
                            ? formatDate((firstOverdue ?? firstExpiring)!.endDate!)
                            : "—"}
                          .
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
        </>
      )}
    </div>
  );
}
