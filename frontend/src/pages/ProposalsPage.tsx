import { AlertTriangle, ArrowRight, Check, Loader2, MapPin, X } from "lucide-react";
import { useState } from "react";
import type { Navigate } from "../app/types";
import { rentedStorage } from "../mocks/customer";
import { MAX_REJECTIONS, proposals as initialProposals, type Proposal } from "../mocks/proposals";
import { ComingSoonDialog } from "../components/ui/ComingSoonDialog";
import { DemoNotice } from "../components/ui/DemoNotice";
import { SurfaceState } from "../components/ui/SurfaceState";

type Tab = "active" | "pending";

const PRIMARY_BUTTON =
  "inline-flex min-h-11 items-center justify-center gap-2.5 rounded-sm border border-transparent bg-brand px-[18px] text-[14px] font-[750] text-background transition-colors duration-[180ms] ease hover:bg-brand-strong disabled:cursor-not-allowed disabled:opacity-60";
const SECONDARY_BUTTON =
  "inline-flex min-h-11 items-center justify-center gap-2.5 rounded-sm border border-brand bg-transparent px-[18px] text-[14px] font-[750] text-brand transition-colors duration-[180ms] ease hover:bg-brand hover:text-background disabled:cursor-not-allowed disabled:opacity-60";
const TAB_BUTTON =
  "border-0 bg-transparent px-1 pb-3 text-[13px] font-bold text-muted hover:text-ink";

export function ProposalsPage({ navigate }: { navigate: Navigate }) {
  const [tab, setTab] = useState<Tab>("pending");
  const [items, setItems] = useState<Proposal[]>(initialProposals);
  const [agreedIds, setAgreedIds] = useState<Set<string>>(new Set());
  const [canceledIds, setCanceledIds] = useState<Set<string>>(new Set());
  const [conflictId, setConflictId] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [rejectingId, setRejectingId] = useState<string | null>(null);
  const [rejectNote, setRejectNote] = useState("");
  const [comingSoonOpen, setComingSoonOpen] = useState(false);

  const pending = items.filter(
    (proposal) => !agreedIds.has(proposal.id) && !canceledIds.has(proposal.id),
  );

  function agree(proposal: Proposal) {
    setBusyId(proposal.id);
    setConflictId(null);
    window.setTimeout(() => {
      setBusyId(null);
      if (proposal.simulateConflict) {
        setConflictId(proposal.id);
        setItems((current) =>
          current.map((item) => (item.id === proposal.id ? { ...item, status: "expired" } : item)),
        );
        return;
      }
      setAgreedIds((current) => new Set(current).add(proposal.id));
    }, 700);
  }

  function confirmReject(proposal: Proposal) {
    const nextCount = proposal.rejectionCount + 1;
    if (nextCount >= MAX_REJECTIONS) {
      setCanceledIds((current) => new Set(current).add(proposal.id));
    } else {
      setItems((current) =>
        current.map((item) =>
          item.id === proposal.id ? { ...item, rejectionCount: nextCount } : item,
        ),
      );
    }
    setRejectingId(null);
    setRejectNote("");
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
          Chờ duyệt ({pending.length})
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
                <button className={SECONDARY_BUTTON} onClick={() => navigate("/my-storage")}>
                  Xem chi tiết <ArrowRight size={16} />
                </button>
              </article>
            ))}
          </div>
        ))}

      {tab === "pending" &&
        (pending.length === 0 ? (
          <SurfaceState
            variant="empty"
            title="Chưa có đề xuất nào đang chờ"
            description="Khi đội ngũ vận hành đề xuất khoang cho yêu cầu của bạn, đề xuất sẽ hiện ở đây."
          />
        ) : (
          <div className="grid gap-4">
            {pending.map((proposal) => {
              const isExpired = proposal.status === "expired";
              const isAgreed = agreedIds.has(proposal.id);
              const isBusy = busyId === proposal.id;
              const nearLimit = proposal.rejectionCount >= MAX_REJECTIONS - 1;
              return (
                <article key={proposal.id} className="border border-border bg-surface p-6">
                  <div className="flex items-start justify-between gap-5 max-[760px]:flex-col">
                    <div>
                      <p className="m-0 font-mono text-[11px] font-bold uppercase tracking-[0.08em] text-brand">
                        Khoang {proposal.unitCode} · {proposal.unitType}
                      </p>
                      <h3 className="mb-0 mt-1 text-[18px] tracking-[-0.02em]">
                        {proposal.facility}
                      </h3>
                      <p className="mb-0 mt-1.5 flex items-center gap-[6px] text-[13px] text-muted">
                        <MapPin size={15} /> {proposal.district}
                      </p>
                    </div>
                    <div className="grid gap-3.5 text-right max-[760px]:text-left">
                      <div className="grid gap-[3px]">
                        <span className="font-mono text-[10px] font-medium uppercase text-muted">
                          Giá thuê đề xuất
                        </span>
                        <strong className="text-[15px]">{proposal.monthlyPrice}</strong>
                      </div>
                      <div className="grid gap-[3px]">
                        <span className="font-mono text-[10px] font-medium uppercase text-muted">
                          {isExpired ? "Đã hết hạn" : "Hạn phản hồi"}
                        </span>
                        <strong className={`text-[13px] ${isExpired ? "text-danger" : ""}`}>
                          {proposal.expiresAt}
                        </strong>
                      </div>
                    </div>
                  </div>

                  {conflictId === proposal.id && (
                    <div className="mt-4">
                      <DemoNotice tone="error">
                        Khoang này vừa có người khác đặt cọc trước. Đề xuất đã hết hiệu lực — đội
                        ngũ vận hành sẽ gửi đề xuất khoang khác cho bạn.
                      </DemoNotice>
                    </div>
                  )}

                  {isExpired && conflictId !== proposal.id && (
                    <div className="mt-4">
                      <SurfaceState
                        variant="session-expired"
                        title="Đề xuất đã hết hạn"
                        description="Bạn chưa phản hồi trước hạn. Đội ngũ vận hành sẽ gửi đề xuất khoang mới."
                      />
                    </div>
                  )}

                  {isAgreed && (
                    <div className="mt-4">
                      <DemoNotice tone="success">
                        Đã đồng ý khoang này. Hoá đơn đặt cọc đang được tạo.
                      </DemoNotice>
                      <button
                        className={`${SECONDARY_BUTTON} mt-3.5`}
                        onClick={() => setComingSoonOpen(true)}
                      >
                        Xem hoá đơn <ArrowRight size={16} />
                      </button>
                    </div>
                  )}

                  {!isExpired && !isAgreed && nearLimit && (
                    <div className="mt-4">
                      <DemoNotice tone="pending">
                        Bạn đã từ chối {proposal.rejectionCount}/{MAX_REJECTIONS} lần cho phép. Từ
                        chối thêm {MAX_REJECTIONS - proposal.rejectionCount} lần nữa, yêu cầu này sẽ
                        tự huỷ và bạn cần gửi lại từ đầu.
                      </DemoNotice>
                    </div>
                  )}

                  {!isExpired && !isAgreed && (
                    <div className="mt-5 flex items-center gap-3">
                      <button
                        className={PRIMARY_BUTTON}
                        disabled={isBusy}
                        onClick={() => agree(proposal)}
                      >
                        {isBusy ? (
                          <Loader2
                            className="animate-[surface-state-spin_0.9s_linear_infinite]"
                            size={16}
                          />
                        ) : (
                          <Check size={16} />
                        )}
                        Đồng ý
                      </button>
                      <button
                        className={SECONDARY_BUTTON}
                        disabled={isBusy}
                        onClick={() => setRejectingId(proposal.id)}
                      >
                        <X size={16} /> Từ chối
                      </button>
                    </div>
                  )}

                  {rejectingId === proposal.id && (
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
                        <button className={PRIMARY_BUTTON} onClick={() => confirmReject(proposal)}>
                          Xác nhận từ chối
                        </button>
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
            {[...canceledIds].length > 0 && (
              <div className="border border-danger bg-[rgba(255,155,115,0.08)] p-5">
                <p className="m-0 flex items-center gap-2 text-[13px] font-bold text-danger">
                  <AlertTriangle size={16} /> Yêu cầu đã bị huỷ
                </p>
                <p className="mb-0 mt-1.5 text-[12px] text-muted">
                  Bạn đã từ chối quá {MAX_REJECTIONS} lần nên yêu cầu này tự động huỷ. Vui lòng gửi
                  yêu cầu mới nếu vẫn cần thuê kho.
                </p>
                <button
                  className={`${SECONDARY_BUTTON} mt-3.5`}
                  onClick={() => navigate("/rental-requests/new")}
                >
                  Gửi yêu cầu mới <ArrowRight size={16} />
                </button>
              </div>
            )}
          </div>
        ))}

      {comingSoonOpen && <ComingSoonDialog onClose={() => setComingSoonOpen(false)} />}
    </main>
  );
}
