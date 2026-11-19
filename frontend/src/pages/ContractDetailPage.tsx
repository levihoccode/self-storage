import {
  AlertTriangle,
  ArrowLeft,
  ArrowRight,
  Download,
  FileText,
  KeyRound,
  MapPin,
} from "lucide-react";
import { useCallback, useState, type ReactNode } from "react";
import type { Navigate } from "../app/types";
import { pathId } from "../app/routes";
import { formatDate, formatDateTime, formatMoney, isoDate } from "../app/format";
import { errorMessage } from "../app/http";
import {
  ACCESS_STATUS,
  ACCESS_TYPE_LABEL,
  cancelReturnRequest,
  CHECKOUT_RESULT_LABEL,
  fetchCheckout,
  fetchFacilities,
  fetchMyReclaimedGoods,
  fetchMyReturnRequests,
  fetchRental,
  INVOICE_STATUS,
  INVOICE_TYPE_LABEL,
  downloadContractPdf,
  openContractDocument,
  requestExtension,
  requestGoodsReturn,
  requestReturn,
  RETURN_STATUS,
  rotateAccessCode,
  useInvoicePayment,
  useResource,
  type CheckoutView,
  type ContractBill,
  type ReclaimedGoods,
  type RentalView,
  type ReturnRequestView,
} from "../app/services/customer";
import { Button } from "../components/ui/Button";
import { ConfirmDialog } from "../components/ui/ConfirmDialog";
import { DemoNotice } from "../components/ui/DemoNotice";
import { DetailPanel } from "../components/ui/DetailPanel";
import { FIELD_LABEL, INPUT_ELEMENT, INPUT_WRAP } from "../components/ui/FormField";
import { UnitLocationCard } from "../components/domain/UnitLocationCard";
import { SectionTitle } from "../components/ui/SectionTitle";
import { StatusTag } from "../components/ui/StatusTag";
import { SurfaceState } from "../components/ui/SurfaceState";

const CONTRACT_STATUS_LABEL: Record<string, string> = {
  Draft: "Bản nháp",
  Signed: "Đã ký",
  Active: "Đang hiệu lực",
  Ended: "Đã kết thúc",
  Canceled: "Đã huỷ",
};

type Notice = { tone: "success" | "error"; message: string } | null;

export function ContractDetailPage({ navigate }: { navigate: Navigate }) {
  const [contractId] = useState(() => pathId(1));

  if (contractId === null) {
    return (
      <SurfaceState
        variant="not-found"
        title="Không tìm thấy hợp đồng"
        description="Đường dẫn không đúng hoặc hợp đồng không thuộc tài khoản của bạn."
        action={{ label: "Về Kho của tôi", onClick: () => navigate("/my-storage") }}
      />
    );
  }
  return <ContractDetail contractId={contractId} navigate={navigate} />;
}

function ContractDetail({ contractId, navigate }: { contractId: number; navigate: Navigate }) {
  const loadRental = useCallback(() => fetchRental(contractId), [contractId]);
  const rental = useResource(loadRental);
  const reclaimed = useResource(fetchMyReclaimedGoods);
  const returns = useResource(fetchMyReturnRequests);
  const facilities = useResource(fetchFacilities);
  const payment = useInvoicePayment(rental.reload);

  const [notice, setNotice] = useState<Notice>(null);
  const [docPending, setDocPending] = useState(false);
  const [pdfPending, setPdfPending] = useState(false);
  const [codeConfirm, setCodeConfirm] = useState(false);
  const [codePending, setCodePending] = useState(false);
  const [accessCode, setAccessCode] = useState<string | null>(null);
  const [cancelTarget, setCancelTarget] = useState<ReturnRequestView | null>(null);
  const [canceling, setCanceling] = useState(false);
  const [checkoutId, setCheckoutId] = useState<number | null>(null);

  function reloadAll() {
    rental.reload();
    returns.reload();
    reclaimed.reload();
  }

  async function openDocument() {
    setDocPending(true);
    setNotice(null);
    try {
      await openContractDocument(contractId);
    } catch (caught) {
      setNotice({ tone: "error", message: errorMessage(caught) });
    } finally {
      setDocPending(false);
    }
  }

  async function downloadPdf() {
    setPdfPending(true);
    setNotice(null);
    try {
      await downloadContractPdf(contractId);
    } catch (caught) {
      setNotice({ tone: "error", message: errorMessage(caught) });
    } finally {
      setPdfPending(false);
    }
  }

  async function newAccessCode() {
    setCodePending(true);
    setNotice(null);
    try {
      const result = await rotateAccessCode(contractId);
      setAccessCode(result.accessCode);
      rental.reload();
    } catch (caught) {
      setNotice({ tone: "error", message: errorMessage(caught) });
    } finally {
      setCodePending(false);
      setCodeConfirm(false);
    }
  }

  async function confirmCancelReturn() {
    if (!cancelTarget) return;
    setCanceling(true);
    try {
      await cancelReturnRequest(cancelTarget.id);
      setNotice({ tone: "success", message: "Đã huỷ yêu cầu trả kho." });
      reloadAll();
    } catch (caught) {
      setNotice({ tone: "error", message: errorMessage(caught) });
    } finally {
      setCanceling(false);
      setCancelTarget(null);
    }
  }

  if (rental.loading && !rental.data) {
    return (
      <div className="grid gap-3" role="status" aria-label="Đang tải">
        <div className="h-[90px] animate-pulse bg-surface" />
        <div className="h-[72px] animate-pulse bg-surface" />
        <div className="h-[240px] animate-pulse bg-surface" />
      </div>
    );
  }
  if (rental.error && !rental.data) {
    const missing = rental.status === 404 || rental.status === 403;
    return (
      <SurfaceState
        variant={missing ? "not-found" : "error"}
        title={missing ? "Không tìm thấy hợp đồng" : "Không tải được hợp đồng"}
        description={rental.error}
        action={
          missing
            ? { label: "Về Kho của tôi", onClick: () => navigate("/my-storage") }
            : { label: "Thử lại", onClick: rental.reload }
        }
      />
    );
  }
  const contract = rental.data;
  if (!contract) return null;

  const facility = facilities.data?.find((item) => item.id === contract.facilityId) ?? null;
  const pastReturns = (returns.data ?? []).filter(
    (item) => item.contractId === contract.contractId && item.id !== contract.returnRequest?.id,
  );
  const activeKeys = contract.accessKeys.filter((key) => key.status === "Active");

  return (
    <div className="grid gap-6">
      <button
        className="inline-flex w-fit items-center gap-1.5 border-0 bg-transparent p-0 text-small font-semibold text-accent hover:text-ink"
        onClick={() => navigate("/my-storage")}
      >
        <ArrowLeft size={16} /> Về Kho của tôi
      </button>

      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <span className="text-label text-muted">
            Khoang {contract.unitCode} · Hợp đồng {contract.contractCode}
          </span>
          {(contract.unitMoves ?? []).map((move) => (
            <span
              key={`${move.fromUnitCode}-${move.movedAt}`}
              className="block text-label text-muted"
            >
              Đã chuyển từ khoang {move.fromUnitCode} sang {move.toUnitCode}
              {move.movedAt ? ` ngày ${formatDateTime(move.movedAt)}` : ""} (khoang cũ hư) — giá
              thuê giữ nguyên; bản hợp đồng đã ký vẫn ghi khoang cũ.
            </span>
          ))}
          <h1 className="mt-1 text-title text-ink">{facility?.name ?? "Hợp đồng thuê kho"}</h1>
          {facility?.address && (
            <p className="mt-1.5 flex items-center gap-1.5 text-small text-muted">
              <MapPin size={15} /> {facility.address}
            </p>
          )}
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <StatusTag
            tone={
              contract.overdue ? "danger" : contract.status === "Active" ? "success" : "neutral"
            }
          >
            {contract.overdue
              ? "Quá hạn"
              : (CONTRACT_STATUS_LABEL[contract.status] ?? contract.status)}
          </StatusTag>
          <Button variant="secondary" pending={docPending} onClick={openDocument}>
            {!docPending && <FileText size={16} />} Xem bản hợp đồng
          </Button>
          <Button variant="secondary" pending={pdfPending} onClick={downloadPdf}>
            {!pdfPending && <Download size={16} />} Tải PDF
          </Button>
        </div>
      </div>

      <UnitLocationCard facilityId={contract.facilityId} unitCode={contract.unitCode} />

      <div className="grid grid-cols-4 gap-2 max-[980px]:grid-cols-2 max-[760px]:grid-cols-1">
        <Summary label="Giá thuê / tháng" value={formatMoney(contract.monthlyPrice)} />
        <Summary label="Tiền cọc" value={formatMoney(contract.depositAmount)} />
        <Summary
          label="Hiệu lực"
          value={`${formatDate(contract.startDate)} → ${formatDate(contract.endDate)}`}
        />
        {contract.status === "Active" ? (
          <Summary
            label={contract.overdue ? "Đã quá hạn" : "Còn lại"}
            value={`${Math.abs(contract.daysLeft)} ngày · kỳ ${contract.period} tháng`}
          />
        ) : (
          // Hợp đồng đã kết thúc/huỷ: số ngày còn lại tính theo end_date cũ không còn ý nghĩa.
          <Summary label="Kỳ thuê" value={`${contract.period} tháng`} />
        )}
      </div>

      {(() => {
        const goods = reclaimed.data?.find((item) => item.contractId === contract.contractId);
        return goods ? (
          <ReclaimedGoodsPanel
            goods={goods}
            onRequested={() => {
              setNotice({
                tone: "success",
                message:
                  "Đã gửi yêu cầu nhận lại đồ. Thanh toán công nợ và phí lưu giữ ở danh sách hoá đơn bên dưới, cơ sở sẽ liên hệ hẹn ngày trả đồ.",
              });
              reloadAll();
            }}
          />
        ) : null;
      })()}

      {contract.status === "Active" && contract.overdue && (
        <OverduePanel
          contract={contract}
          payingId={payment.payingId}
          onPay={(invoiceId) => payment.pay(invoiceId)}
          onExtended={(bill) => {
            setNotice({
              tone: "success",
              message: `Đã tạo hoá đơn gia hạn ${bill.code} · ${formatMoney(bill.amount)}. Thanh toán để cộng thêm kỳ thuê — truy cập được mở lại ngay sau khi trả.`,
            });
            rental.reload();
          }}
          onReturned={() => {
            setNotice({
              tone: "success",
              message:
                "Đã gửi yêu cầu trả kho. Cơ sở sẽ phân công nhân viên và báo lịch hẹn; khi bạn đến buổi hẹn, khoang được mở để dọn đồ.",
            });
            reloadAll();
          }}
        />
      )}
      {notice && <DemoNotice tone={notice.tone}>{notice.message}</DemoNotice>}

      <div className="grid grid-cols-[minmax(0,1fr)_320px] items-start gap-6 max-[980px]:grid-cols-1">
        <div className="grid gap-3">
          <section className="bg-surface p-5">
            <SectionTitle>Hoá đơn của hợp đồng</SectionTitle>
            {payment.payError && <DemoNotice tone="error">{payment.payError.message}</DemoNotice>}
            {contract.invoices.length === 0 ? (
              <p className="mt-3 text-small text-muted">Chưa có hoá đơn nào gắn với hợp đồng.</p>
            ) : (
              <div className="mt-3 grid gap-2">
                {contract.invoices.map((bill) => (
                  <BillRow
                    key={bill.invoiceId}
                    bill={bill}
                    paying={payment.payingId === bill.invoiceId}
                    onPay={() => payment.pay(bill.invoiceId)}
                  />
                ))}
              </div>
            )}
          </section>

          <section className="bg-surface p-5">
            <SectionTitle
              action={
                <Button
                  variant="secondary"
                  size="sm"
                  disabled={contract.status !== "Active" || contract.accessSuspended}
                  onClick={() => {
                    setAccessCode(null);
                    setCodeConfirm(true);
                  }}
                >
                  <KeyRound size={14} /> Lấy mã khoá số mới
                </Button>
              }
            >
              Quyền truy cập
            </SectionTitle>
            {contract.accessSuspended && (
              <p className="mt-3 bg-danger-soft p-3 text-small text-danger">
                Quyền truy cập đang tạm khoá do quá hạn (mã số đã thu hồi). Trả phí quá hạn và gia
                hạn, hoặc đến buổi trả kho để được mở lại — sau đó lấy mã khoá số mới tại đây.
              </p>
            )}
            {accessCode && (
              <div className="mt-4 border-2 border-brand bg-surface-subtle p-4 text-center">
                <span className="text-label text-muted">Mã khoá số mới — chỉ hiển thị một lần</span>
                <strong className="mt-1 block font-mono text-[2.25rem] tracking-[0.3em] text-ink">
                  {accessCode}
                </strong>
                <p className="mt-1 text-label text-muted">
                  Hãy lưu lại mã này. Mã cũ đã bị thu hồi.
                </p>
              </div>
            )}
            {contract.accessKeys.length === 0 ? (
              <p className="mt-3 text-small text-muted">Chưa có quyền truy cập nào được cấp.</p>
            ) : (
              <div className="mt-3 grid gap-2">
                {contract.accessKeys.map((key) => {
                  const status = ACCESS_STATUS[key.status] ?? {
                    label: key.status,
                    tone: "neutral" as const,
                  };
                  return (
                    <div
                      key={key.id}
                      className="flex items-center justify-between gap-3 bg-surface-subtle p-3"
                    >
                      <span className="text-small text-ink">
                        {ACCESS_TYPE_LABEL[key.accessType] ?? key.accessType}
                        {key.accessType === "PhysicalKey" && key.quantity
                          ? ` · ${key.quantity} chìa`
                          : ""}
                      </span>
                      <StatusTag tone={status.tone}>{status.label}</StatusTag>
                    </div>
                  );
                })}
              </div>
            )}
            {activeKeys.length > 0 && (
              <p className="mt-3 text-label text-muted">
                Mã khoá số được lưu dạng mã hoá nên không xem lại được — dùng "Lấy mã khoá số mới"
                khi quên mã.
              </p>
            )}
          </section>

          <section className="bg-surface p-5">
            <SectionTitle>Trả kho</SectionTitle>
            {contract.returnRequest ? (
              <ReturnRow
                request={contract.returnRequest}
                onCancel={() => setCancelTarget(contract.returnRequest)}
                onCheckout={() => setCheckoutId(contract.returnRequest?.id ?? null)}
              />
            ) : (
              <p className="mt-3 text-small text-muted">Chưa có yêu cầu trả kho đang mở.</p>
            )}
            {returns.error && !returns.data && (
              <p className="mt-3 text-label text-danger">
                Không tải được lịch sử trả kho: {returns.error}
              </p>
            )}
            {pastReturns.length > 0 && (
              <div className="mt-4 grid gap-2">
                <span className="text-label text-muted">Yêu cầu trước đây</span>
                {pastReturns.map((item) => (
                  <ReturnRow
                    key={item.id}
                    request={item}
                    onCheckout={() => setCheckoutId(item.id)}
                  />
                ))}
              </div>
            )}
          </section>
        </div>

        <aside className="grid gap-3">
          {/* Còn phí quá hạn chưa trả: backend chặn gia hạn (canExtend = false) — trả phí trước rồi mới gia hạn. */}
          {!contract.canExtend &&
            contract.status === "Active" &&
            !contract.overdue &&
            (() => {
              const overdueFee = contract.invoices.find(
                (bill) => bill.code.startsWith("INV-OVD-") && bill.status === "Unpaid",
              );
              return overdueFee ? (
                <div className="bg-warning-soft p-4 text-small text-warning">
                  <strong className="block">Thanh toán phí quá hạn trước khi gia hạn</strong>
                  Còn phí quá hạn {formatMoney(overdueFee.amount)} chưa thanh toán (hoá đơn{" "}
                  {overdueFee.code}). Thanh toán hoá đơn này ở danh sách hoá đơn bên dưới, sau đó
                  mới gia hạn được. Nếu trả kho, phí sẽ được trừ vào tiền cọc.
                </div>
              ) : null;
            })()}
          {contract.canExtend && !contract.overdue && (
            <ExtendPanel
              contractId={contract.contractId}
              monthlyPrice={contract.monthlyPrice}
              onDone={(bill) => {
                setNotice({
                  tone: "success",
                  message: `Đã tạo hoá đơn gia hạn ${bill.code} · ${formatMoney(bill.amount)}. Thanh toán để cộng thêm kỳ thuê.`,
                });
                rental.reload();
              }}
            />
          )}
          {contract.canRequestReturn && !contract.overdue && (
            <ReturnPanel
              contractId={contract.contractId}
              onDone={() => {
                setNotice({
                  tone: "success",
                  message: "Đã gửi yêu cầu trả kho. Cơ sở sẽ phân công nhân viên và báo lịch hẹn.",
                });
                reloadAll();
              }}
            />
          )}
          {!contract.canExtend &&
            !contract.canRequestReturn &&
            !contract.overdue &&
            (contract.invoices.some(
              (bill) => bill.type === "Extension" && bill.status === "Unpaid",
            ) ? (
              <div className="bg-surface-subtle p-4 text-small text-muted">
                Đang có hoá đơn gia hạn chờ thanh toán. Thanh toán hoặc đợi hoá đơn hết hạn rồi mới
                gửi yêu cầu trả kho.
              </div>
            ) : (
              <div className="bg-surface-subtle p-4 text-small text-muted">
                Hợp đồng hiện không có thao tác gia hạn hoặc trả kho.
              </div>
            ))}
          <div className="bg-[var(--dark)] p-4 text-[var(--on-dark-ink)]">
            <h2 className="text-small font-semibold">Cần hỗ trợ?</h2>
            <p className="mt-1.5 text-small text-[rgba(233,234,230,.7)]">
              Báo mất chìa, sự cố khoang hoặc thắc mắc hoá đơn của hợp đồng này.
            </p>
            <Button variant="onDark" className="mt-4" onClick={() => navigate("/support")}>
              Gửi yêu cầu hỗ trợ <ArrowRight size={16} />
            </Button>
          </div>
        </aside>
      </div>

      {codeConfirm && (
        <ConfirmDialog
          title="Lấy mã khoá số mới?"
          description="Mã đang dùng sẽ bị thu hồi ngay và mã mới chỉ hiển thị một lần trên màn hình này."
          confirmLabel="Lấy mã mới"
          tone="default"
          isPending={codePending}
          onConfirm={newAccessCode}
          onCancel={() => setCodeConfirm(false)}
        />
      )}
      {cancelTarget && (
        <ConfirmDialog
          title="Huỷ yêu cầu trả kho?"
          description={`Yêu cầu trả kho ngày ${formatDate(cancelTarget.preferredDate)} và lịch hẹn liên quan (nếu có) sẽ bị huỷ.`}
          confirmLabel="Huỷ yêu cầu"
          cancelLabel="Giữ yêu cầu"
          isPending={canceling}
          onConfirm={confirmCancelReturn}
          onCancel={() => setCancelTarget(null)}
        />
      )}
      {checkoutId !== null && (
        <CheckoutPanel
          returnRequestId={checkoutId}
          onClose={() => setCheckoutId(null)}
          onPay={(invoiceId) => payment.pay(invoiceId)}
          payingId={payment.payingId}
          payError={payment.payError?.message ?? null}
        />
      )}
    </div>
  );
}

function Summary({ label, value }: { label: string; value: string }) {
  return (
    <div className="bg-surface p-4">
      <span className="text-label text-muted">{label}</span>
      <strong className="mt-1 block text-data text-ink">{value}</strong>
    </div>
  );
}

function BillRow({
  bill,
  paying,
  onPay,
}: {
  bill: ContractBill;
  paying: boolean;
  onPay: () => void;
}) {
  const status = INVOICE_STATUS[bill.status] ?? { label: bill.status, tone: "neutral" as const };
  return (
    <div className="grid grid-cols-[minmax(0,1fr)_auto_auto] items-center gap-3 bg-surface-subtle p-3 max-[760px]:grid-cols-1">
      <div className="min-w-0">
        <span className="text-label text-muted">
          {bill.code} · {INVOICE_TYPE_LABEL[bill.type] ?? bill.type}
        </span>
        <strong className="block truncate text-small font-semibold text-ink">{bill.title}</strong>
        <span className="text-label text-muted">Hạn {formatDateTime(bill.dueDate)}</span>
      </div>
      <div className="text-right max-[760px]:text-left">
        <strong className="block text-data text-ink">{formatMoney(bill.amount)}</strong>
        <StatusTag tone={status.tone}>{status.label}</StatusTag>
      </div>
      {bill.status === "Unpaid" ? (
        <Button size="sm" pending={paying} onClick={onPay}>
          Thanh toán
        </Button>
      ) : (
        <span />
      )}
    </div>
  );
}

function ReturnRow({
  request,
  onCancel,
  onCheckout,
}: {
  request: ReturnRequestView;
  onCancel?: () => void;
  onCheckout: () => void;
}) {
  const status = RETURN_STATUS[request.status] ?? {
    label: request.status,
    tone: "neutral" as const,
  };
  // Khách huỷ được tới khi FS xác nhận khách đến buổi trả kho (sau đó backend trả 409).
  const open =
    (request.status === "Pending" || request.status === "Assigned") &&
    !request.appointmentArrivedAt;
  return (
    <div className="mt-3 bg-surface-subtle p-4">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <span className="text-label text-muted">Ngày mong muốn</span>
          <strong className="block text-small font-semibold text-ink">
            {formatDate(request.preferredDate)}
          </strong>
          {request.appointmentStart && (
            <span className="mt-1 block text-label text-muted">
              Lịch hẹn: {formatDateTime(request.appointmentStart)}
            </span>
          )}
          {request.note && (
            <span className="mt-1 block text-label text-muted">Ghi chú: {request.note}</span>
          )}
          {request.cancelReason && (
            <span className="mt-1 block text-label text-muted">
              Lý do huỷ: {request.cancelReason}
            </span>
          )}
        </div>
        <StatusTag tone={status.tone}>{status.label}</StatusTag>
      </div>
      <div className="mt-3 flex flex-wrap gap-2">
        {request.status !== "Canceled" && request.status !== "Pending" && (
          <Button variant="secondary" size="sm" onClick={onCheckout}>
            Xem biên bản trả kho
          </Button>
        )}
        {open && onCancel && (
          <Button variant="danger" size="sm" onClick={onCancel}>
            Huỷ yêu cầu
          </Button>
        )}
      </div>
    </div>
  );
}

function ExtendPanel({
  contractId,
  monthlyPrice,
  onDone,
}: {
  contractId: number;
  monthlyPrice: number;
  onDone: (bill: ContractBill) => void;
}) {
  const [months, setMonths] = useState("1");
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const count = Number(months);

  async function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setPending(true);
    setError(null);
    try {
      onDone(await requestExtension(contractId, count));
    } catch (caught) {
      setError(errorMessage(caught));
    } finally {
      setPending(false);
    }
  }

  return (
    <form className="bg-surface p-4" onSubmit={submit}>
      <SectionTitle>Gia hạn hợp đồng</SectionTitle>
      <p className="mt-2 text-label text-muted">
        Hệ thống tạo hoá đơn gia hạn; kỳ thuê chỉ được cộng sau khi thanh toán.
      </p>
      <label className={`${FIELD_LABEL} mt-3`} htmlFor="extend-months">
        Số tháng gia hạn
      </label>
      <div className={INPUT_WRAP}>
        <input
          id="extend-months"
          type="number"
          min={1}
          step={1}
          required
          value={months}
          onChange={(event) => setMonths(event.target.value)}
          className={INPUT_ELEMENT}
        />
        <span className="shrink-0 text-label text-muted">tháng</span>
      </div>
      {count > 0 && (
        <p className="mt-2 text-label text-muted">
          Tạm tính: {formatMoney(Number(monthlyPrice) * count)}
        </p>
      )}
      {error && <DemoNotice tone="error">{error}</DemoNotice>}
      <Button type="submit" className="mt-3 w-full" pending={pending} disabled={!(count > 0)}>
        Tạo hoá đơn gia hạn
      </Button>
    </form>
  );
}

/**
 * Hợp đồng quá hạn: hai cách xử lý trong một khung — (1) trả phí quá hạn rồi gia hạn (truy cập mở lại ngay sau khi
 * trả), (2) trả kho (khoang mở để dọn đồ khi nhân viên xác nhận bạn đến buổi hẹn; nợ trừ vào cọc). Số tiền trả kho là
 * tạm tính: phí còn tăng tới ngày buổi trả kho, chưa gồm phí vệ sinh / hư hỏng / mất chìa.
 */
function OverduePanel({
  contract,
  payingId,
  onPay,
  onExtended,
  onReturned,
}: {
  contract: RentalView;
  payingId: number | null;
  onPay: (invoiceId: number) => void;
  onExtended: (bill: ContractBill) => void;
  onReturned: () => void;
}) {
  const days = Math.abs(contract.daysLeft);
  const fee =
    contract.invoices.find(
      (bill) => bill.code.startsWith("INV-OVD-") && bill.status === "Unpaid",
    ) ?? null;
  const otherDebt = contract.invoices
    // Quyết toán trả kho gộp hoá đơn Unpaid + tiền thuê Expired (nợ thuê chưa trả).
    .filter(
      (bill) =>
        (bill.status === "Unpaid" &&
          bill.invoiceId !== fee?.invoiceId &&
          bill.type !== "Extension") ||
        (bill.status === "Expired" && bill.type === "Rental"),
    )
    .reduce((sum, bill) => sum + Number(bill.amount), 0);
  const owed = Number(fee?.amount ?? 0) + otherDebt;
  const balance = Number(contract.depositAmount) - owed;
  // Hợp đồng đang khoá: khi khách đến buổi trả kho, mọi khoản nợ gộp vào một hoá đơn INV-DBT — trả xong mới mở khoang.
  const moveOutDebt =
    contract.invoices.find(
      (bill) => bill.code.startsWith("INV-DBT-") && bill.status === "Unpaid",
    ) ?? null;
  const extensionPending = contract.invoices.some(
    (bill) => bill.type === "Extension" && bill.status === "Unpaid",
  );
  const request = contract.returnRequest;

  return (
    <section className="border-l-4 border-danger bg-surface p-5">
      <h2 className="m-0 flex items-center gap-2 text-section text-danger">
        <AlertTriangle size={18} /> Hợp đồng đã quá hạn {days} ngày
      </h2>
      <p className="mt-1 text-small text-muted">
        Kết thúc ngày {formatDate(contract.endDate)}.{" "}
        {contract.accessSuspended
          ? "Quyền truy cập khoang đang tạm khoá — chọn một trong hai cách dưới đây để được mở lại."
          : "Phí quá hạn tính theo ngày sau thời gian ân hạn — chọn một trong hai cách dưới đây."}
      </p>
      {contract.reclaimDate && (
        <p className="m-0 mt-2 bg-danger-soft p-3 text-small text-danger">
          Nếu tới ngày {formatDate(contract.reclaimDate)} bạn vẫn chưa xử lý, cơ sở sẽ thu hồi
          khoang: mở khoang có biên bản, chuyển đồ về khu lưu giữ (tính phí thu hồi và phí lưu giữ
          theo ngày). Hết hạn lưu giữ mà không nhận lại, đồ được thanh lý để trừ nợ.
        </p>
      )}
      <dl className="mb-0 mt-4 grid grid-cols-3 gap-2 text-small max-[760px]:grid-cols-1">
        <div className="bg-surface-subtle p-3">
          <dt className="text-label text-muted">Phí quá hạn hiện tại</dt>
          <dd className="m-0 mt-1 font-semibold text-ink">
            {fee ? `${formatMoney(fee.amount)} · ${fee.code}` : "Chưa phát sinh (còn ân hạn)"}
          </dd>
        </div>
        <div className="bg-surface-subtle p-3">
          <dt className="text-label text-muted">Hoá đơn khác chưa trả</dt>
          <dd className="m-0 mt-1 font-semibold text-ink">{formatMoney(otherDebt)}</dd>
        </div>
        <div className="bg-surface-subtle p-3">
          <dt className="text-label text-muted">Tiền cọc đang giữ</dt>
          <dd className="m-0 mt-1 font-semibold text-ink">{formatMoney(contract.depositAmount)}</dd>
        </div>
      </dl>

      <div className="mt-4 grid grid-cols-2 gap-4 max-[980px]:grid-cols-1">
        <div className="grid content-start gap-3 bg-surface-subtle p-4">
          <strong className="text-small text-ink">Cách 1 — Trả phí và gia hạn</strong>
          <ol className="m-0 grid gap-1 pl-5 text-small text-muted">
            <li>Thanh toán phí quá hạn (bắt buộc trước khi gia hạn).</li>
            <li>Tạo hoá đơn gia hạn và thanh toán.</li>
            <li>
              Truy cập được mở lại ngay sau khi trả — lấy mã khoá số mới trong trang này. Khoang
              dùng chìa đã bị gắn khoá chặn của cơ sở thì nhân viên gỡ và bạn nhận thông báo.
            </li>
          </ol>
          {fee && (
            <Button pending={payingId === fee.invoiceId} onClick={() => onPay(fee.invoiceId)}>
              Thanh toán phí {formatMoney(fee.amount)}
            </Button>
          )}
          {contract.canExtend ? (
            <ExtendPanel
              contractId={contract.contractId}
              monthlyPrice={contract.monthlyPrice}
              onDone={onExtended}
            />
          ) : (
            <p className="m-0 text-label text-muted">
              {request
                ? "Đang có yêu cầu trả kho — không gia hạn được."
                : extensionPending
                  ? "Đã có hoá đơn gia hạn chờ thanh toán ở danh sách hoá đơn."
                  : fee
                    ? "Form gia hạn hiện ra sau khi bạn thanh toán phí quá hạn."
                    : "Hợp đồng hiện không gia hạn được."}
            </p>
          )}
        </div>

        <div className="grid content-start gap-3 bg-surface-subtle p-4">
          <strong className="text-small text-ink">Cách 2 — Trả kho</strong>
          {contract.accessSuspended ? (
            <ul className="m-0 grid gap-1 pl-5 text-small text-muted">
              <li>
                Khi bạn đến buổi trả kho, hệ thống chốt phí quá hạn tới ngày đó và gộp mọi khoản còn
                nợ thành một hoá đơn.
              </li>
              <li>
                Thanh toán hết hoá đơn đó (ngay tại buổi hẹn) thì khoang mới được mở để bạn dọn đồ.
              </li>
              <li>
                Tiền cọc giữ nguyên — khi quyết toán chỉ trừ phí vệ sinh, hư hỏng, mất chìa (nếu có)
                rồi hoàn phần dư.
              </li>
              <li>
                Cần thanh toán khoảng <strong className="text-ink">{formatMoney(owed)}</strong>{" "}
                (tính tới hôm nay; phí quá hạn còn tăng tới ngày buổi hẹn).
              </li>
            </ul>
          ) : (
            <ul className="m-0 grid gap-1 pl-5 text-small text-muted">
              <li>
                Khi bạn đến buổi trả kho, nhân viên kiểm tra khoang cùng bạn rồi chốt biên bản.
              </li>
              <li>Phí quá hạn dừng ở ngày buổi trả kho và được trừ vào tiền cọc khi quyết toán.</li>
              <li>
                Tạm tính hiện tại:{" "}
                <strong className="text-ink">
                  {balance >= 0
                    ? `được hoàn khoảng ${formatMoney(balance)}`
                    : `phải trả thêm khoảng ${formatMoney(-balance)}`}
                </strong>{" "}
                (chưa gồm phí vệ sinh, hư hỏng, mất chìa nếu có).
              </li>
            </ul>
          )}
          {moveOutDebt && (
            <Button
              pending={payingId === moveOutDebt.invoiceId}
              onClick={() => onPay(moveOutDebt.invoiceId)}
            >
              Thanh toán {formatMoney(moveOutDebt.amount)} để mở khoang
            </Button>
          )}
          {request ? (
            <p className="m-0 bg-surface p-3 text-small text-ink">
              Đã gửi yêu cầu trả kho ngày {formatDate(request.preferredDate)} —{" "}
              {RETURN_STATUS[request.status]?.label ?? request.status}
              {request.appointmentStart
                ? ` · lịch hẹn ${formatDateTime(request.appointmentStart)}`
                : " · chờ cơ sở phân công và báo lịch"}
              .
            </p>
          ) : contract.canRequestReturn ? (
            <ReturnPanel contractId={contract.contractId} onDone={onReturned} />
          ) : (
            <p className="m-0 text-label text-muted">
              Đang có hoá đơn gia hạn chờ thanh toán — thanh toán hoặc đợi hoá đơn hết hạn rồi mới
              gửi yêu cầu trả kho.
            </p>
          )}
        </div>
      </div>
    </section>
  );
}

/**
 * Đồ của khách cơ sở đã thu hồi từ khoang (hợp đồng quá hạn kéo dài): đang lưu giữ tới ngày X — khách yêu cầu nhận
 * lại (lập hoá đơn phí lưu giữ) rồi trả công nợ + phí lưu giữ ở danh sách hoá đơn; hoặc đã trả đồ / đã thanh lý.
 */
function ReclaimedGoodsPanel({
  goods,
  onRequested,
}: {
  goods: ReclaimedGoods;
  onRequested: () => void;
}) {
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function request() {
    setPending(true);
    setError(null);
    try {
      await requestGoodsReturn(goods.id);
      onRequested();
    } catch (caught) {
      setError(errorMessage(caught));
    } finally {
      setPending(false);
    }
  }

  return (
    <section className="border-l-4 border-danger bg-surface p-5">
      <h2 className="m-0 flex items-center gap-2 text-section text-danger">
        <AlertTriangle size={18} /> Khoang đã bị thu hồi do quá hạn
      </h2>
      <p className="mt-1 text-small text-muted">
        Cơ sở thu hồi khoang {goods.unitCode} ngày {formatDateTime(goods.reclaimedAt)}, có nhân viên
        chứng kiến{goods.witnessName ? ` (${goods.witnessName})` : ""} và biên bản kiểm kê.
      </p>
      <dl className="mb-0 mt-4 grid grid-cols-3 gap-2 text-small max-[760px]:grid-cols-1">
        <div className="bg-surface-subtle p-3">
          <dt className="text-label text-muted">Trạng thái đồ</dt>
          <dd className="m-0 mt-1 font-semibold text-ink">
            {goods.status === "Holding"
              ? `Đang lưu giữ tới ${formatDate(goods.holdUntil)}`
              : goods.status === "Returned"
                ? `Đã trả cho bạn ${formatDateTime(goods.returnedAt)}`
                : `Đã thanh lý ${formatDateTime(goods.liquidatedAt)}`}
          </dd>
        </div>
        <div className="bg-surface-subtle p-3">
          <dt className="text-label text-muted">Công nợ chưa trả</dt>
          <dd className="m-0 mt-1 font-semibold text-ink">{formatMoney(goods.outstandingDebt)}</dd>
        </div>
        <div className="bg-surface-subtle p-3">
          <dt className="text-label text-muted">Phí lưu giữ</dt>
          <dd className="m-0 mt-1 font-semibold text-ink">
            {formatMoney(goods.holdFeePerDay)} / ngày · đã giữ {goods.daysHeld} ngày
          </dd>
        </div>
      </dl>
      <div className="mt-3 bg-surface-subtle p-3 text-small text-ink">
        <span className="text-label text-muted">Biên bản kiểm kê</span>
        <p className="m-0 mt-1 whitespace-pre-line">{goods.inventoryNote}</p>
        {goods.photos.length > 0 && (
          <div className="mt-2 flex flex-wrap gap-2">
            {goods.photos.map((url) => (
              <a key={url} href={url} target="_blank" rel="noreferrer">
                <img src={url} alt="Ảnh kiểm kê" className="h-20 w-20 object-cover" />
              </a>
            ))}
          </div>
        )}
      </div>
      {goods.status === "Holding" &&
        (goods.returnRequestedAt ? (
          <p className="m-0 mt-3 text-small text-ink">
            Đã gửi yêu cầu nhận lại {formatDateTime(goods.returnRequestedAt)}.{" "}
            {goods.outstandingDebt > 0
              ? "Thanh toán công nợ và phí lưu giữ ở danh sách hoá đơn bên dưới, rồi đến cơ sở nhận đồ."
              : "Bạn đã trả hết — liên hệ cơ sở để hẹn ngày nhận đồ."}
          </p>
        ) : (
          <div className="mt-3 grid gap-2">
            <p className="m-0 text-small text-muted">
              Để nhận lại đồ: gửi yêu cầu, hệ thống lập hoá đơn phí lưu giữ tới hôm nay; thanh toán
              hết công nợ + phí lưu giữ rồi đến cơ sở nhận. Hết hạn lưu giữ mà không nhận lại, đồ
              được thanh lý để trừ nợ, tiền dư (nếu có) được hoàn cho bạn.
            </p>
            <Button pending={pending} onClick={request}>
              Yêu cầu nhận lại đồ
            </Button>
          </div>
        ))}
      {goods.status === "Liquidated" && (
        <p className="m-0 mt-3 text-small text-ink">
          Thu được {formatMoney(goods.proceedsAmount ?? 0)} từ thanh lý.{" "}
          {Number(goods.surplusAmount ?? 0) > 0
            ? goods.surplusRefundedAt
              ? `Cơ sở đã hoàn tiền dư ${formatMoney(goods.surplusAmount ?? 0)}.`
              : `Tiền dư ${formatMoney(goods.surplusAmount ?? 0)} sẽ được cơ sở hoàn cho bạn.`
            : goods.outstandingDebt > 0
              ? "Tiền thanh lý chưa đủ trả nợ — vui lòng thanh toán công nợ còn lại."
              : "Công nợ đã được trả hết."}
        </p>
      )}
      {error && <DemoNotice tone="error">{error}</DemoNotice>}
    </section>
  );
}

function ReturnPanel({ contractId, onDone }: { contractId: number; onDone: () => void }) {
  const [date, setDate] = useState("");
  const [note, setNote] = useState("");
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setPending(true);
    setError(null);
    try {
      await requestReturn(contractId, date, note.trim() || null);
      setDate("");
      setNote("");
      onDone();
    } catch (caught) {
      setError(errorMessage(caught));
    } finally {
      setPending(false);
    }
  }

  return (
    <form className="bg-surface p-4" onSubmit={submit}>
      <SectionTitle>Yêu cầu trả kho</SectionTitle>
      <p className="mt-2 text-label text-muted">
        Chọn ngày bạn muốn trả kho; cơ sở sẽ phân công nhân viên và báo khung giờ.
      </p>
      <label className={`${FIELD_LABEL} mt-3`} htmlFor="return-date">
        Ngày mong muốn
      </label>
      <div className={INPUT_WRAP}>
        <input
          id="return-date"
          type="date"
          required
          min={isoDate(new Date())}
          value={date}
          onChange={(event) => setDate(event.target.value)}
          className={INPUT_ELEMENT}
        />
      </div>
      <label className={`${FIELD_LABEL} mt-3`} htmlFor="return-note">
        Ghi chú (tuỳ chọn)
      </label>
      <textarea
        id="return-note"
        maxLength={1000}
        value={note}
        onChange={(event) => setNote(event.target.value)}
        className="min-h-[64px] w-full rounded-lg border border-rule-strong bg-surface p-2.5 text-small text-ink"
      />
      {error && <DemoNotice tone="error">{error}</DemoNotice>}
      <Button
        type="submit"
        variant="secondary"
        className="mt-3 w-full"
        pending={pending}
        disabled={!date}
      >
        Gửi yêu cầu trả kho
      </Button>
    </form>
  );
}

function CheckoutPanel({
  returnRequestId,
  onClose,
  onPay,
  payingId,
  payError,
}: {
  returnRequestId: number;
  onClose: () => void;
  onPay: (invoiceId: number) => void;
  payingId: number | null;
  payError: string | null;
}) {
  const load = useCallback(() => fetchCheckout(returnRequestId), [returnRequestId]);
  const checkout = useResource(load);
  const data: CheckoutView | null = checkout.data;

  let body: ReactNode;
  if (checkout.loading && !data) {
    body = <SurfaceState variant="loading" title="Đang tải biên bản…" />;
  } else if (checkout.error && !data) {
    body = (
      <SurfaceState
        variant="error"
        title="Không tải được biên bản trả kho"
        description={checkout.error}
        action={{ label: "Thử lại", onClick: checkout.reload }}
      />
    );
  } else if (!data || data.result === null) {
    body = (
      <SurfaceState
        variant="empty"
        title="Chưa có biên bản"
        description="Biên bản được lập khi nhân viên kiểm tra khoang trong buổi trả kho."
      />
    );
  } else {
    body = (
      <>
        <dl className="grid gap-2.5">
          <Row label="Kết quả" value={CHECKOUT_RESULT_LABEL[data.result] ?? data.result} />
          <Row label="Khách đến lúc" value={formatDateTime(data.arrivedAt)} />
          <Row label="Kiểm tra lúc" value={formatDateTime(data.inspectedAt)} />
          <Row label="Khoang đã trống" value={data.unitEmptied ? "Có" : "Chưa"} />
          <Row label="Cần vệ sinh" value={data.cleaningRequired ? "Có" : "Không"} />
          <Row
            label="Chìa đã trả"
            value={`${data.returnedKeys ?? 0} / ${data.keysIssued} chìa đã giao`}
          />
          {data.damages && <Row label="Hư hỏng" value={data.damages} />}
          {data.note && <Row label="Ghi chú" value={data.note} />}
        </dl>
        <h3 className="mt-6 text-small font-semibold text-ink">Quyết toán</h3>
        <dl className="mt-2.5 grid gap-2.5">
          <Row label="Tiền cọc" value={formatMoney(data.depositAmount)} />
          <Row
            label="Phí vệ sinh"
            value={formatMoney(data.cleaningRequired ? data.cleaningFee : 0)}
          />
          <Row label="Phí hư hỏng" value={formatMoney(data.damageAmount)} />
          <Row label="Phí mất chìa (mỗi chìa)" value={formatMoney(data.lostKeyFee)} />
          <Row
            label="Hoá đơn chưa trả của hợp đồng"
            value={formatMoney(data.unpaidContractInvoices)}
          />
          <Row label="Tổng phí" value={formatMoney(data.totalCharges)} />
          <Row label="Tiền hoàn lại" value={formatMoney(data.refundAmount)} />
          <Row label="Hoàn tất lúc" value={formatDateTime(data.completedAt)} />
        </dl>
        {data.photos.length > 0 && (
          <div className="mt-5 grid grid-cols-3 gap-2">
            {data.photos.map((url) => (
              <a key={url} href={url} target="_blank" rel="noreferrer">
                <img src={url} alt="Ảnh biên bản trả kho" className="h-20 w-full object-cover" />
              </a>
            ))}
          </div>
        )}
        {data.shortfallInvoice && (
          <div className="mt-5 bg-warning-soft p-4 text-warning">
            <p className="flex items-center gap-2 text-small font-semibold">
              <AlertTriangle size={16} /> Phần phí vượt tiền cọc:{" "}
              {formatMoney(data.shortfallInvoice.amount)}
            </p>
            <p className="mt-1 text-label">
              {data.shortfallInvoice.code} ·{" "}
              {INVOICE_STATUS[data.shortfallInvoice.status]?.label ?? data.shortfallInvoice.status}
            </p>
            {payError && <DemoNotice tone="error">{payError}</DemoNotice>}
            {data.shortfallInvoice.status === "Unpaid" && (
              <Button
                className="mt-3"
                size="sm"
                pending={payingId === data.shortfallInvoice.invoiceId}
                onClick={() => data.shortfallInvoice && onPay(data.shortfallInvoice.invoiceId)}
              >
                Thanh toán
              </Button>
            )}
          </div>
        )}
      </>
    );
  }

  return (
    <DetailPanel title="Biên bản trả kho" eyebrow={`Yêu cầu #${returnRequestId}`} onClose={onClose}>
      {body}
    </DetailPanel>
  );
}

function Row({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-start justify-between gap-4 text-small">
      <dt className="text-muted">{label}</dt>
      <dd className="m-0 text-right font-semibold text-ink">{value}</dd>
    </div>
  );
}
