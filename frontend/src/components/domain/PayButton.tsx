import { CreditCard, Loader2 } from "lucide-react";
import { useState } from "react";
import { paymentGateway } from "../../app/payments";

/**
 * Nút "Thanh toán": gọi BE tạo giao dịch rồi chuyển khách sang cổng VNPay.
 * Chỉ render khi hóa đơn Unpaid — việc quyết định đó thuộc về caller (dữ liệu từ API).
 * Khóa nút sau khi bấm để giảm va chạm bấm đúp / 2 tab (spec: 04-invoices-payment.md).
 */
export function PayButton({
  invoiceId,
  label = "Thanh toán",
}: {
  invoiceId: string;
  label?: string;
}) {
  const [isPending, setIsPending] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleClick() {
    setIsPending(true);
    setError(null);
    const result = await paymentGateway.startPayment(invoiceId);
    if (result.ok) {
      // Giữ nút khóa trong lúc trình duyệt chuyển trang.
      window.location.assign(result.paymentUrl);
      return;
    }
    setError(result.message);
    setIsPending(false);
  }

  return (
    <div className="grid gap-1.5">
      <button
        className="inline-flex min-h-11 items-center justify-center gap-2.5 rounded-sm border border-transparent bg-brand px-[18px] text-[14px] font-[750] text-background transition-colors duration-[180ms] ease hover:bg-brand-strong disabled:cursor-not-allowed disabled:opacity-60"
        onClick={handleClick}
        disabled={isPending}
      >
        {isPending ? (
          <Loader2 className="animate-[surface-state-spin_0.9s_linear_infinite]" size={16} />
        ) : (
          <CreditCard size={16} />
        )}
        {isPending ? "Đang chuyển sang VNPay…" : label}
      </button>
      {error && (
        <p className="m-0 text-[12px] text-danger" role="alert">
          {error}
        </p>
      )}
    </div>
  );
}
