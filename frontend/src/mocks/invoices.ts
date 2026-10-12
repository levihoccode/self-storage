export type InvoiceType = "Deposit" | "Rental" | "Extension" | "Penalty" | "Service";
export type InvoiceStatus = "Unpaid" | "Paid" | "Canceled";

export type Invoice = {
  id: string;
  code: string;
  type: InvoiceType;
  title: string;
  description: string;
  amount: number;
  discountAmount: number;
  status: InvoiceStatus;
  dueDate: string;
  orderId: string;
  unitCode: string;
  facilityId: string;
  /** Demo-only: shows the "unit taken by someone else" failure instead of success. */
  simulateFailure?: boolean;
};

export const INVOICE_TYPE_LABEL: Record<InvoiceType, string> = {
  Deposit: "Đặt cọc",
  Rental: "Tiền thuê",
  Extension: "Gia hạn",
  Penalty: "Phí phát sinh",
  Service: "Dịch vụ",
};

export const invoices: Invoice[] = [
  {
    id: "invoice-501",
    code: "HD-000501",
    type: "Deposit",
    title: "Đặt cọc khoang C-112",
    description: "Tiền cọc giữ chỗ cho khoang C-112, Kho Mộc — Thảo Điền Hub.",
    amount: 980000,
    discountAmount: 0,
    status: "Unpaid",
    dueDate: "09/10/2026",
    orderId: "order-501",
    unitCode: "C-112",
    facilityId: "thao-dien",
  },
  {
    id: "invoice-502",
    code: "HD-000502",
    type: "Deposit",
    title: "Đặt cọc khoang D-045",
    description: "Tiền cọc giữ chỗ cho khoang D-045, Kho Mộc — Tân Bình Hub.",
    amount: 1680000,
    discountAmount: 0,
    status: "Unpaid",
    dueDate: "05/10/2026",
    orderId: "order-502",
    unitCode: "D-045",
    facilityId: "tan-binh",
    simulateFailure: true,
  },
  {
    id: "invoice-401",
    code: "HD-000401",
    type: "Rental",
    title: "Tiền thuê tháng 03/2026",
    description: "Tiền thuê khoang A-208 kỳ tháng 03/2026.",
    amount: 1450000,
    discountAmount: 0,
    status: "Paid",
    dueDate: "12/03/2026",
    orderId: "order-401",
    unitCode: "A-208",
    facilityId: "thao-dien",
  },
  {
    id: "invoice-402",
    code: "HD-000402",
    type: "Extension",
    title: "Gia hạn hợp đồng 3 tháng",
    description: "Phí gia hạn hợp đồng khoang B-014 thêm 3 tháng.",
    amount: 2460000,
    discountAmount: 120000,
    status: "Paid",
    dueDate: "20/09/2026",
    orderId: "order-402",
    unitCode: "B-014",
    facilityId: "tan-binh",
  },
  {
    id: "invoice-403",
    code: "HD-000403",
    type: "Service",
    title: "Hỗ trợ đóng gói tại kho",
    description: "Dịch vụ đóng gói hỗ trợ khi bàn giao khoang.",
    amount: 350000,
    discountAmount: 0,
    status: "Canceled",
    dueDate: "01/09/2026",
    orderId: "order-403",
    unitCode: "A-070",
    facilityId: "thao-dien",
  },
];

/**
 * DEMO ONLY — in-memory "paid" tracking so InvoicesPage and
 * AppointmentBookingPage agree on deposit status across navigation without a
 * backend. Real state lives in `Invoice.status` once the API exists.
 */
const paidInvoiceIds = new Set(
  invoices.filter((invoice) => invoice.status === "Paid").map((invoice) => invoice.id),
);

export function isInvoicePaid(invoiceId: string) {
  return paidInvoiceIds.has(invoiceId);
}

export function markInvoicePaid(invoiceId: string) {
  paidInvoiceIds.add(invoiceId);
}

export function findDepositInvoiceByOrderId(orderId: string) {
  return invoices.find((invoice) => invoice.orderId === orderId && invoice.type === "Deposit");
}
