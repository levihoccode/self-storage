import { AlertTriangle, ArrowRight, Check, MapPin, X } from "lucide-react";
import { useCallback, useEffect, useState } from "react";
import { ApiError } from "../app/api";
import {
  agreeProposal,
  fetchMyProposals,
  formatDate,
  formatMoney,
  rejectProposal,
} from "../app/proposals";
import type { CustomerProposal, DepositInvoice, Navigate } from "../app/types";
import { rentedStorage } from "../mocks/customer";
import { Button } from "../components/ui/Button";
import { DemoNotice } from "../components/ui/DemoNotice";
import { SurfaceState } from "../components/ui/SurfaceState";

type Tab = "active" | "pending";
type LoadState = "loading" | "ready" | "error";

/** Kết quả thao tác trên một thẻ, giữ lại để hiển thị sau khi proposal rời khỏi danh sách Pending. */
type CardOutcome =
  | { kind: "agreed"; invoice: DepositInvoice }
  | { kind: "unavailable"; message: string }
  | { kind: "rejected" };

type LoadResult = { ok: true; data: CustomerProposal[] } | { ok: false; message: string };

async function loadPendingProposals(): Promise<LoadResult> {
  try {
    return { ok: true, data: await fetchMyProposals("Pending") };
  } catch (error) {
    return {
      ok: false,
      message: error instanceof Error ? error.message : "Không tải được danh sách đề xuất.",
    };
  }
}

const TAB_BUTTON =
  "border-0 bg-transparent px-1 pb-3 text-[13px] font-bold text-muted hover:text-ink";

export function ProposalsPage({ navigate }: { navigate: Navigate }) {
  const [tab, setTab] = useState<Tab>("pending");
  const [items, setItems] = useState<CustomerProposal[]>([]);
  const [loadState, setLoadState] = useState<LoadState>("loading");
  const [loadError, setLoadError] = useState("");
  const [outcomes, setOutcomes] = useState<Record<number, CardOutcome>>({});
  const [actionError, setActionError] = useState<{ id: number; message: string } | null>(null);
  const [orderCanceled, setOrderCanceled] = useState(false);
  const [busyId, setBusyId] = useState<number | null>(null);
  const [rejectingId, setRejectingId] = useState<number | null>(null);
  const [rejectNote, setRejectNote] = useState("");

  const applyLoad = useCallback((result: LoadResult) => {
    if (result.ok) {
      setItems(result.data);
      setLoadState("ready");
    } else {
      setLoadError(result.message);
      setLoadState("error");
    }
  }, []);

  // setState chỉ chạy sau await; `active` chặn cập nhật khi đã rời trang.
  useEffect(() => {
    let active = true;
    async function loadInitial() {
      const result = await loadPendingProposals();
      if (active) applyLoad(result);
    }
    void loadInitial();
    return () => {
      active = false;
    };
  }, [applyLoad]);

  // Nút "Thử lại" — setState nằm ở event handler, không phải effect.
  async function reload() {
    setLoadState("loading");
    applyLoad(await loadPendingProposals());
  }

  // Proposal đã thao tác vẫn giữ trên màn để khách thấy kết quả; chỉ proposal còn chờ mới được đếm.
  const awaitingCount = items.filter((proposal) => !outcomes[proposal.id]).length;

  async function agree(proposal: CustomerProposal) {
    setBusyId(proposal.id);
    setActionError(null);
    try {
      const result = await agreeProposal(proposal.id);
      setOutcomes((current) => ({
        ...current,
        [proposal.id]: { kind: "agreed", invoice: result.invoice },
      }));
    } catch (error) {
      const message = error instanceof Error ? error.message : "Không thể đồng ý đề xuất.";
      // 409: khoang vừa bị đặt cọc / bảo trì — BE đã huỷ proposal này và báo FM đề xuất khoang khác.
      if (error instanceof ApiError && error.status === 409) {
        setOutcomes((current) => ({ ...current, [proposal.id]: { kind: "unavailable", message } }));
      } else {
        setActionError({ id: proposal.id, message });
      }
    } finally {
      setBusyId(null);
    }
  }

  async function confirmReject(proposal: CustomerProposal) {
    setBusyId(proposal.id);
    setActionError(null);
    try {
      const result = await rejectProposal(proposal.id, rejectNote.trim());
      setOutcomes((current) => ({ ...current, [proposal.id]: { kind: "rejected" } }));
      if (result.orderCanceled) setOrderCanceled(true);
      setRejectingId(null);
      setRejectNote("");
    } catch (error) {
      const message = error instanceof Error ? error.message : "Không thể từ chối đề xuất.";
      setActionError({ id: proposal.id, message });
    } finally {
      setBusyId(null);
    }
  }

  return (
    <main>
      <div className="mb-8">
        <h1 className="m-0 text-[28px] tracking-[-0.03em] text-ink">Kiểm tra kho của tôi</h1>
        <p className="mt-2 text-[13px] text-muted">
          Xem lại đề xuất khoang từ đội ngũ vận hành và xác nhận trước khi đặt cọc.
        </p>
      </div>
      <div className="mb-6 flex items-center gap-6 border-b border-border">
        <button
          className={`${TAB_BUTTON} ${tab === "active" ? "border-b-2 border-brand text-ink" : ""}`}
          onClick={() => setTab("active")}
        >
          Đang sử dụng ({rentedStorage.length})
        </button>
        <button
          className={`${TAB_BUTTON} ${tab === "pending" ? "border-b-2 border-brand text-ink" : ""}`}
          onClick={() => setTab("pending")}
        >
          Chờ duyệt ({awaitingCount})
        </button>
      </div>

      {tab === "active" &&
        (rentedStorage.length === 0 ? (
          <SurfaceState variant="empty" title="Chưa có kho đang sử dụng" />
        ) : (
          <div className="grid gap-3.5">
            {rentedStorage.map((storage) => (
              <article
                key={storage.id}
                className="flex items-center justify-between gap-5 border border-border bg-surface p-5 max-[760px]:flex-col max-[760px]:items-stretch"
              >
                <div>
                  <p className="m-0 font-mono text-[11px] font-bold uppercase tracking-[0.08em] text-brand">
                    Khoang {storage.unitCode}
                  </p>
                  <h3 className="mb-0 mt-1 text-[16px] tracking-[-0.02em]">{storage.facility}</h3>
                  <p className="mb-0 mt-1.5 flex items-center gap-[6px] text-[12px] text-muted">
                    <MapPin size={14} /> {storage.district}
                  </p>
                </div>
                <Button variant="secondary" onClick={() => navigate("/my-storage")}>
                  Xem chi tiết <ArrowRight size={16} />
                </Button>
              </article>
            ))}
          </div>
        ))}

      {tab === "pending" && loadState === "loading" && (
        <SurfaceState variant="loading" title="Đang tải đề xuất khoang…" />
      )}

      {tab === "pending" && loadState === "error" && (
        <SurfaceState
          variant="error"
          title="Không tải được đề xuất khoang"
          description={loadError}
          action={{ label: "Thử lại", onClick: () => void reload() }}
        />
      )}

      {tab === "pending" &&
        loadState === "ready" &&
        (items.length === 0 ? (
          <SurfaceState
            variant="empty"
            title="Chưa có đề xuất nào đang chờ"
            description="Khi đội ngũ vận hành đề xuất khoang cho yêu cầu của bạn, đề xuất sẽ hiện ở đây."
          />
        ) : (
          <div className="grid gap-4">
            {items.map((proposal) => {
              const outcome = outcomes[proposal.id];
              const isBusy = busyId === proposal.id;
              // Ngưỡng và số lần đã từ chối lấy từ API (proposal.max_rejection_count), FE không tự đặt.
              const remaining = proposal.maxRejectionCount - proposal.rejectionCount;
              const nearLimit = remaining <= 1;
              return (
                <article key={proposal.id} className="border border-border bg-surface p-6">
                  <div className="flex items-start justify-between gap-5 max-[760px]:flex-col">
                    <div>
                      <p className="m-0 font-mono text-[11px] font-bold uppercase tracking-[0.08em] text-brand">
                        Khoang {proposal.unitCode} · {proposal.unitTypeName}
                      </p>
                      <h3 className="mb-0 mt-1 text-[18px] tracking-[-0.02em]">
                        {proposal.facilityName}
                      </h3>
                      {proposal.facilityAddress && (
                        <p className="mb-0 mt-1.5 flex items-center gap-[6px] text-[13px] text-muted">
                          <MapPin size={15} /> {proposal.facilityAddress}
                        </p>
                      )}
                    </div>
                    <div className="grid gap-3.5 text-right max-[760px]:text-left">
                      <div className="grid gap-[3px]">
                        <span className="font-mono text-[10px] font-medium uppercase text-muted">
                          Giá thuê đề xuất
                        </span>
                        <strong className="text-[15px]">
                          {formatMoney(proposal.monthlyPrice)} / tháng
                        </strong>
                      </div>
                      <div className="grid gap-[3px]">
                        <span className="font-mono text-[10px] font-medium uppercase text-muted">
                          Ngày đề xuất
                        </span>
                        <strong className="text-[13px]">{formatDate(proposal.createdAt)}</strong>
                      </div>
                    </div>
                  </div>

                  {outcome?.kind === "unavailable" && (
                    <div className="mt-4">
                      <DemoNotice tone="error">
                        {outcome.message}. Đề xuất đã hết hiệu lực — đội ngũ vận hành sẽ gửi đề xuất
                        khoang khác cho bạn.
                      </DemoNotice>
                    </div>
                  )}

                  {outcome?.kind === "agreed" && (
                    <div className="mt-4">
                      <DemoNotice tone="success">
                        Đã đồng ý khoang này. Hoá đơn đặt cọc {outcome.invoice.code}:{" "}
                        {formatMoney(outcome.invoice.amount)}, hạn thanh toán{" "}
                        {formatDate(outcome.invoice.dueDate)}.
                      </DemoNotice>
                      <Button
                        variant="secondary"
                        className="mt-3.5"
                        onClick={() => navigate("/invoices")}
                      >
                        Xem hoá đơn <ArrowRight size={16} />
                      </Button>
                    </div>
                  )}

                  {outcome?.kind === "rejected" && (
                    <div className="mt-4">
                      <DemoNotice tone="pending">
                        Đã từ chối khoang này. Đội ngũ vận hành sẽ đề xuất khoang khác cho bạn.
                      </DemoNotice>
                    </div>
                  )}

                  {!outcome && nearLimit && (
                    <div className="mt-4">
                      <DemoNotice tone="pending">
                        Bạn đã từ chối {proposal.rejectionCount}/{proposal.maxRejectionCount} lần
                        cho phép. Từ chối thêm {remaining} lần nữa, yêu cầu này sẽ tự huỷ và bạn cần
                        gửi lại từ đầu.
                      </DemoNotice>
                    </div>
                  )}

                  {actionError?.id === proposal.id && (
                    <div className="mt-4">
                      <DemoNotice tone="error">{actionError.message}</DemoNotice>
                    </div>
                  )}

                  {!outcome && (
                    <div className="mt-5 flex items-center gap-3">
                      <Button pending={isBusy} onClick={() => void agree(proposal)}>
                        {!isBusy && <Check size={16} />}
                        Đồng ý
                      </Button>
                      <Button
                        variant="secondary"
                        disabled={isBusy}
                        onClick={() => setRejectingId(proposal.id)}
                      >
                        <X size={16} /> Từ chối
                      </Button>
                    </div>
                  )}

                  {!outcome && rejectingId === proposal.id && (
                    <div className="mt-4 border border-border bg-surface-subtle p-4">
                      <label className="grid gap-1.5 text-[12px] font-bold text-ink">
                        Lý do từ chối
                        <textarea
                          className="min-h-[70px] rounded-sm border border-border bg-surface p-2.5 text-[13px] font-normal text-ink"
                          value={rejectNote}
                          onChange={(event) => setRejectNote(event.target.value)}
                          placeholder="Cho đội ngũ vận hành biết vì sao khoang này chưa phù hợp…"
                        />
                      </label>
                      <div className="mt-3 flex items-center gap-2.5">
                        <Button
                          pending={isBusy}
                          disabled={!rejectNote.trim()}
                          onClick={() => void confirmReject(proposal)}
                        >
                          Xác nhận từ chối
                        </Button>
                        <button
                          className="border-0 bg-transparent px-3 py-2 text-[12px] text-muted hover:text-brand"
                          onClick={() => {
                            setRejectingId(null);
                            setRejectNote("");
                          }}
                        >
                          Huỷ
                        </button>
                      </div>
                    </div>
                  )}
                </article>
              );
            })}
            {orderCanceled && (
              <div className="border border-danger bg-danger/8 p-5">
                <p className="m-0 flex items-center gap-2 text-[13px] font-bold text-danger">
                  <AlertTriangle size={16} /> Yêu cầu đã bị huỷ
                </p>
                <p className="mb-0 mt-1.5 text-[12px] text-muted">
                  Bạn đã từ chối quá số khoang cho phép nên yêu cầu này đã bị huỷ. Vui lòng gửi yêu
                  cầu mới nếu vẫn cần thuê kho.
                </p>
                <Button
                  variant="secondary"
                  className="mt-3.5"
                  onClick={() => navigate("/rental-requests/new")}
                >
                  Gửi yêu cầu mới <ArrowRight size={16} />
                </Button>
              </div>
            )}
          </div>
        ))}
    </main>
  );
}
