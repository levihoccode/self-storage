import { apiRequest } from "./api";
import { authGateway } from "./auth";

export type FmTransactionKind = "INVOICE" | "FEE" | "PAYMENT" | "REFUND";

export type FmTransactionDirection = "IN" | "OUT";

export type FmTransactionStatus =
  "Unpaid" | "Paid" | "Canceled" | "Expired" | "Pending" | "Failed" | "Success" | "Duplicate";

export type FmInvoiceRefundStatus =
  "None" | "Required" | "Processing" | "AwaitingConfirmation" | "Refunded";

export type FmTransaction = {
  id: number;
  kind: FmTransactionKind;
  direction: FmTransactionDirection;
  amount: number;
  status: FmTransactionStatus;
  occurredAt: string;
  orderId: number | null;
  invoiceId: number | null;
  invoiceCode: string | null;
  paymentTransactionId: number | null;
  refundTransactionId: number | null;
  code: string;
  method: string | null;
  description: string;
  customerId: number;
  customerName: string | null;
  customerEmail: string;
  facilityId: number;
  facilityName: string;
  unitCode: string | null;
  invoiceRefundStatus: FmInvoiceRefundStatus | null;
  invoiceRefundReason: string | null;
  pendingAgeMinutes: number;
  needsReconciliation: boolean;
  refundConfirmedAt: string | null;
  refundDisputedAt: string | null;
  refundDisputeNote: string | null;
};

export type FmTransactionPage = {
  items: FmTransaction[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};

export type FmTransactionFilters = {
  kind?: FmTransactionKind;
  direction?: FmTransactionDirection;
  status?: FmTransactionStatus;
  refundStatus?: FmInvoiceRefundStatus;
  /** Chỉ giữ PAYMENT Pending đã quá cửa sổ đối soát. */
  needsReconciliation?: boolean;
  /** ISO-8601 UTC, bao gồm. */
  from?: string;
  /** ISO-8601 UTC, loại trừ. */
  to?: string;
  page: number;
  size: number;
};

type ManagedFacility = {
  id: number;
};

/** Lịch sử giao dịch của cơ sở FM hiện tại; backend tự chặn nếu facility ngoài phạm vi. */
export async function loadFacilityTransactions(
  filters: FmTransactionFilters,
  signal?: AbortSignal,
): Promise<FmTransactionPage> {
  const accessToken = authGateway.getSession()?.token;
  if (!accessToken) {
    throw new Error("Phiên đăng nhập đã hết. Vui lòng đăng nhập lại.");
  }

  const facility = await apiRequest<ManagedFacility>("/api/fm/facility", {
    accessToken,
    signal,
  });
  const params = new URLSearchParams({
    facilityId: String(facility.id),
    page: String(filters.page),
    size: String(filters.size),
  });
  if (filters.kind) params.set("kind", filters.kind);
  if (filters.direction) params.set("direction", filters.direction);
  if (filters.status) params.set("status", filters.status);
  if (filters.refundStatus) params.set("refundStatus", filters.refundStatus);
  if (filters.needsReconciliation) params.set("needsReconciliation", "true");
  if (filters.from) params.set("from", filters.from);
  if (filters.to) params.set("to", filters.to);

  return apiRequest<FmTransactionPage>(`/api/fm/transactions?${params.toString()}`, {
    accessToken,
    signal,
  });
}
