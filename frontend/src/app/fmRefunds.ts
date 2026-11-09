import { apiRequest } from "./api";
import { authGateway } from "./auth";
import type { RefundCasePage } from "./adminReconciliation";

type ManagedFacility = {
  id: number;
};

/** Queue invoice đang chờ hoàn tiền theo cơ sở của FM hiện tại. */
export async function loadFmRefundCases(page: number, size = 20): Promise<RefundCasePage> {
  const accessToken = authGateway.getSession()?.token;
  if (!accessToken) {
    throw new Error("Phiên đăng nhập đã hết. Vui lòng đăng nhập lại.");
  }
  const facility = await apiRequest<ManagedFacility>("/api/fm/facility", { accessToken });
  const params = new URLSearchParams({
    facilityId: String(facility.id),
    page: String(page),
    size: String(size),
  });
  return apiRequest<RefundCasePage>(`/api/refunds/queues/invoices?${params.toString()}`, {
    accessToken,
  });
}
