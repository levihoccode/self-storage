import { apiRequest } from "./api";
import { authGateway } from "./auth";

/** Trạng thái `RentalRequest` — đúng giá trị BE trả (`RentalRequestStatus`). */
export type RentalRequestStatus = "Pending" | "Rejected" | "Approved" | "Converted" | "Expired";

/** Bộ lý do chuẩn (`db-table-draft.md` › RentalRequest); `OTHER` bắt buộc ghi chú. */
export type RejectReason = "UNIT_UNAVAILABLE" | "UNIT_MISMATCH" | "CUSTOMER_REQUEST" | "OTHER";

/** Một dòng trong hàng đợi FM — `GET /api/fm/rental-requests` (không có field facility). */
export type RentalRequestSummary = {
  id: number;
  customerName: string;
  customerEmail: string;
  customerPhone: string;
  unitTypeId: number;
  startDate: string;
  period: number;
  status: RentalRequestStatus;
  createdAt: string;
  expiresAt: string | null;
};

export type RentalRequestPage = {
  items: RentalRequestSummary[];
  page: number;
  size: number;
  total: number;
};

export type ApproveRentalRequestResult = {
  id: number;
  status: RentalRequestStatus;
  unitId: number;
  respondedAt: string;
  expiresAt: string;
};

export type RejectRentalRequestResult = {
  id: number;
  status: RentalRequestStatus;
  rejectReason: RejectReason;
  respondedAt: string;
};

/** Loại khoang cơ sở cung cấp — `GET /api/unit-types?facilityId=` (public). */
export type UnitType = {
  id: number;
  name: string;
  width: number;
  depth: number;
  height: number;
  area: number;
  monthlyPrice: number;
};

/** Khoang để FM chọn khi duyệt. */
export type SelectableUnit = {
  id: number;
  code: string;
  unitTypeId: number;
  status: "Available" | "Maintenance";
};

function accessToken(): string | undefined {
  return authGateway.getSession()?.token;
}

export function fetchRentalRequests(
  facilityId: number,
  status: RentalRequestStatus | undefined,
  page: number,
  size: number,
): Promise<RentalRequestPage> {
  const params = new URLSearchParams({
    facilityId: String(facilityId),
    page: String(page),
    size: String(size),
  });
  if (status) params.set("status", status);
  return apiRequest<RentalRequestPage>(`/api/fm/rental-requests?${params}`, {
    accessToken: accessToken(),
  });
}

/** 409 khi khoang hết `Available`, yêu cầu đã được FM khác xử lý hoặc đã quá hạn. */
export function approveRentalRequest(
  requestId: number,
  unitId: number,
): Promise<ApproveRentalRequestResult> {
  return apiRequest<ApproveRentalRequestResult>(`/api/fm/rental-requests/${requestId}/approve`, {
    method: "POST",
    body: { unitId },
    accessToken: accessToken(),
  });
}

export function rejectRentalRequest(
  requestId: number,
  rejectReason: RejectReason,
  rejectNote?: string,
): Promise<RejectRentalRequestResult> {
  return apiRequest<RejectRentalRequestResult>(`/api/fm/rental-requests/${requestId}/reject`, {
    method: "POST",
    body: { rejectReason, rejectNote: rejectNote || undefined },
    accessToken: accessToken(),
  });
}

export function fetchUnitTypes(facilityId: number): Promise<UnitType[]> {
  return apiRequest<UnitType[]>(`/api/unit-types?facilityId=${facilityId}`);
}

/**
 * TẠM — chưa có route trả cơ sở của FM đang đăng nhập (levihoccode, #71). BE chỉ quan hệ qua
 * `facilities.fm_account_id`; tạm map theo seed: fm1 → Q7 (id 1), fm2 → TD (id 2).
 * Bỏ khi có `GET /api/fm/facility`.
 */
const FACILITY_ID_BY_FM_EMAIL: Record<string, number> = {
  "fm1@lemar.vn": 1,
  "fm2@lemar.vn": 2,
};

export function currentFmFacilityId(): number | null {
  const email = authGateway.getSession()?.user.email.toLowerCase();
  return email ? (FACILITY_ID_BY_FM_EMAIL[email] ?? null) : null;
}

/**
 * TẠM — chưa có API liệt kê khoang (levihoccode, #71). Danh sách theo seed `backend/sql/seed.sql`:
 * 8 khoang đều ở Q7, TD không có khoang. Trạng thái có thể lệch DB thật; BE vẫn kiểm lại khi duyệt
 * và trả 409 nếu khoang không còn `Available`. Bỏ khi có `GET /api/fm/storage-units`.
 */
const SEED_UNITS_BY_FACILITY: Record<number, SelectableUnit[]> = {
  1: [
    { id: 1, code: "A-01", unitTypeId: 1, status: "Available" },
    { id: 2, code: "A-02", unitTypeId: 1, status: "Available" },
    { id: 3, code: "A-03", unitTypeId: 1, status: "Available" },
    { id: 4, code: "A-04", unitTypeId: 1, status: "Available" },
    { id: 5, code: "B-01", unitTypeId: 2, status: "Available" },
    { id: 6, code: "B-02", unitTypeId: 2, status: "Available" },
    { id: 7, code: "B-03", unitTypeId: 2, status: "Available" },
    { id: 8, code: "C-01", unitTypeId: 3, status: "Maintenance" },
  ],
  2: [],
};

export function selectableUnits(facilityId: number, unitTypeId: number): SelectableUnit[] {
  return (SEED_UNITS_BY_FACILITY[facilityId] ?? []).filter(
    (unit) => unit.unitTypeId === unitTypeId && unit.status === "Available",
  );
}

export function formatDate(iso: string): string {
  const date = new Date(iso);
  const pad = (value: number) => String(value).padStart(2, "0");
  return `${pad(date.getDate())}/${pad(date.getMonth() + 1)}/${date.getFullYear()}`;
}

export function formatDateTime(iso: string): string {
  const date = new Date(iso);
  const pad = (value: number) => String(value).padStart(2, "0");
  return `${formatDate(iso)} ${pad(date.getHours())}:${pad(date.getMinutes())}`;
}
