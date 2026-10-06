export type StartPaymentResult = { ok: true; paymentUrl: string } | { ok: false; message: string };

export interface PaymentGateway {
  startPayment(invoiceId: string): Promise<StartPaymentResult>;
}

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL as string | undefined;

/**
 * Gọi POST /api/invoices/{id}/pay (specs: fe-pages/customer/04-invoices-payment.md).
 * FE chỉ gửi invoiceId — số tiền do BE đọc từ hóa đơn, FE không bao giờ truyền số tiền.
 */
const httpGateway: PaymentGateway = {
  async startPayment(invoiceId) {
    try {
      const response = await fetch(
        `${API_BASE_URL}/api/invoices/${encodeURIComponent(invoiceId)}/pay`,
        {
          method: "POST",
          headers: { Accept: "application/json" },
        },
      );
      if (!response.ok) {
        return { ok: false, message: "Không thể tạo giao dịch thanh toán. Vui lòng thử lại." };
      }
      const body = (await response.json()) as { paymentUrl?: string };
      if (!body.paymentUrl) {
        return { ok: false, message: "Máy chủ chưa trả về đường dẫn thanh toán." };
      }
      return { ok: true, paymentUrl: body.paymentUrl };
    } catch {
      return { ok: false, message: "Không kết nối được máy chủ. Vui lòng thử lại." };
    }
  },
};

let demoCallCount = 0;

/**
 * DEMO ONLY — REMOVE WHEN THE BACKEND ENDPOINT POST /api/invoices/{id}/pay EXISTS.
 * Không có VNPay thật: lần bấm lẻ giả lập thanh toán thành công, lần bấm chẵn giả lập thất bại,
 * để thử được cả hai popup. Đặt VITE_API_BASE_URL để chuyển sang gọi BE thật.
 */
const demoGateway: PaymentGateway = {
  async startPayment(invoiceId) {
    await new Promise((resolve) => window.setTimeout(resolve, 600));
    demoCallCount += 1;
    const code = demoCallCount % 2 === 1 ? "00" : "24";
    const params = new URLSearchParams({
      vnp_ResponseCode: code,
      vnp_TxnRef: `DEMO${invoiceId}`,
    });
    return { ok: true, paymentUrl: `/payment/result?${params.toString()}` };
  },
};

export const paymentGateway: PaymentGateway = API_BASE_URL ? httpGateway : demoGateway;
