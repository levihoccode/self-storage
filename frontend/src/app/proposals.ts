import { apiRequest } from "./api";
import { authGateway } from "./auth";
import type {
  AgreeProposalResult,
  CustomerProposal,
  FmRentalOrder,
  ProposalSnapshot,
  ProposalStatus,
  RejectProposalResult,
} from "./types";

/**
 * API Flow 1.3 (B3, #113): khách đồng ý / từ chối đề xuất khoang, FM đề xuất lại.
 * Lỗi nghiệp vụ (400/403/404/409) ném `ApiError` kèm message tiếng Việt của BE — trang hiển thị nguyên văn.
 */
function accessToken(): string | undefined {
  return authGateway.getSession()?.token;
}

export function fetchMyProposals(status?: ProposalStatus): Promise<CustomerProposal[]> {
  const query = status ? `?status=${status}` : "";
  return apiRequest<CustomerProposal[]>(`/api/customer/proposals${query}`, {
    accessToken: accessToken(),
  });
}

/** 409 khi khoang không còn khả dụng (proposal đã bị huỷ ở BE) hoặc proposal đã được xử lý. */
export function agreeProposal(proposalId: number): Promise<AgreeProposalResult> {
  return apiRequest<AgreeProposalResult>(`/api/customer/proposals/${proposalId}/agree`, {
    method: "POST",
    accessToken: accessToken(),
  });
}

export function rejectProposal(proposalId: number, note: string): Promise<RejectProposalResult> {
  return apiRequest<RejectProposalResult>(`/api/customer/proposals/${proposalId}/reject`, {
    method: "POST",
    body: { note },
    accessToken: accessToken(),
  });
}

export function fetchRentalOrder(orderId: number): Promise<FmRentalOrder> {
  return apiRequest<FmRentalOrder>(`/api/fm/rental-orders/${orderId}`, {
    accessToken: accessToken(),
  });
}

export function reproposeUnit(
  orderId: number,
  unitId: number,
  overrideReason?: string,
): Promise<ProposalSnapshot> {
  return apiRequest<ProposalSnapshot>(`/api/fm/rental-orders/${orderId}/re-propose`, {
    method: "POST",
    body: { unitId, overrideReason: overrideReason || undefined },
    accessToken: accessToken(),
  });
}

/** Ngày giờ ISO → `dd/MM/yyyy` (quy ước hiển thị của FE). */
export function formatDate(iso: string): string {
  const date = new Date(iso);
  const pad = (value: number) => String(value).padStart(2, "0");
  return `${pad(date.getDate())}/${pad(date.getMonth() + 1)}/${date.getFullYear()}`;
}

/** Số tiền → `1.500.000 đ`. */
export function formatMoney(value: number): string {
  return `${new Intl.NumberFormat("vi-VN").format(value)} đ`;
}
