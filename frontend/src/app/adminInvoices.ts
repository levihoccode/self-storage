import { apiRequest } from "./api";
import { authGateway } from "./auth";

export type AdminInvoice = {
  id: number;
  orderId: number | null;
  code: string;
  title: string;
  description: string | null;
  type: string;
  status: "Unpaid" | "Paid" | "Canceled" | "Expired";
  amount: number;
  createdAt: string;
  dueDate: string | null;
  unitId: number | null;
  unitCode: string | null;
  facilityId: number | null;
  facilityName: string | null;
};

export type AdminInvoicePage = {
  items: AdminInvoice[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};

function token() {
  return authGateway.getSession()?.token;
}

export function loadAdminInvoices(page: number, size = 20): Promise<AdminInvoicePage> {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  return apiRequest<AdminInvoicePage>(`/api/admin/invoices?${params.toString()}`, {
    accessToken: token(),
  });
}
