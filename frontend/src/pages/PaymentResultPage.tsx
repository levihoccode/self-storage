import { CheckCircle2, XCircle } from "lucide-react";
import { useState } from "react";
import { SurfaceState } from "../components/ui/SurfaceState";
import type { Navigate } from "../app/types";

const SUCCESS_CODE = "00";

/** Mã lỗi VNPay hay gặp; mã khác dùng thông điệp chung. */
const FAILURE_REASON: Record<string, string> = {
  "24": "Bạn đã hủy giao dịch.",
  "11": "Giao dịch đã hết thời gian chờ thanh toán.",
  "51": "Tài khoản không đủ số dư.",
  "65": "Tài khoản đã vượt hạn mức giao dịch trong ngày.",
};

/**
 * Trang khách được đưa về sau khi rời cổng VNPay.
 * Kết quả đọc từ thanh địa chỉ nên CHỈ để hiển thị — trạng thái thật do IPN ghi ở BE,
 * khi có API hóa đơn thì trang phải refetch trạng thái hóa đơn trước khi tin "đã thanh toán".
 */
export function PaymentResultPage({ navigate }: { navigate: Navigate }) {
  const params = new URLSearchParams(window.location.search);
  const code = params.get("vnp_ResponseCode");
  const txnRef = params.get("vnp_TxnRef") ?? "";
  const [open, setOpen] = useState(true);

  if (code === null) {
    return (
      <SurfaceState
        variant="empty"
        title="Chưa có kết quả thanh toán"
        description="Trang này hiển thị kết quả sau khi bạn thanh toán qua VNPay."
        action={{ label: "Về kho của tôi", onClick: () => navigate("/my-storage") }}
      />
    );
  }

  const isSuccess = code === SUCCESS_CODE;
  const title = isSuccess ? "Thanh toán thành công" : "Thanh toán không thành công";
  const description = isSuccess
    ? "Cảm ơn bạn. Hệ thống đang ghi nhận khoản thanh toán, trạng thái hóa đơn sẽ được cập nhật trong giây lát."
    : (FAILURE_REASON[code] ?? "Giao dịch chưa hoàn tất. Bạn có thể thử thanh toán lại.");

  return (
    <>
      <SurfaceState
        variant={isSuccess ? "empty" : "error"}
        title={title}
        description={description}
        action={{ label: "Về kho của tôi", onClick: () => navigate("/my-storage") }}
      />
      {open && (
        <div
          className="fixed inset-0 z-[90] grid place-items-center bg-[rgba(7,16,19,0.68)] p-5"
          role="presentation"
          onClick={() => setOpen(false)}
        >
          <div
            className="w-[min(420px,100%)] rounded-md border border-border bg-surface p-7 text-center shadow-soft-token"
            role="alertdialog"
            aria-modal="true"
            aria-labelledby="payment-result-title"
            onClick={(event) => event.stopPropagation()}
          >
            <span
              className={`mb-3 inline-grid place-items-center ${isSuccess ? "text-success" : "text-danger"}`}
            >
              {isSuccess ? <CheckCircle2 size={44} /> : <XCircle size={44} />}
            </span>
            <h2
              id="payment-result-title"
              className="mb-[10px] mt-0 text-[19px] leading-[1.2] tracking-[-0.03em]"
            >
              {title}
            </h2>
            <p className="mb-2 mt-0 text-[13px] leading-normal text-muted">{description}</p>
            {txnRef && (
              <p className="mb-[22px] mt-0 font-mono text-[11px] text-muted">
                Mã giao dịch: {txnRef}
              </p>
            )}
            <button
              className="inline-flex min-h-11 items-center justify-center rounded-sm border-0 bg-brand px-[18px] text-label text-background transition-colors hover:bg-brand-strong"
              onClick={() => setOpen(false)}
              autoFocus
            >
              Đóng
            </button>
          </div>
        </div>
      )}
    </>
  );
}
