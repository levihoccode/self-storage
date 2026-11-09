/**
 * Catalog công khai (cơ sở + loại khoang). Hình dạng khớp đúng contract đã implement:
 * - `GET /api/facilities`                → `Facility[]`
 * - `GET /api/unit-types?facilityId={id}` → `UnitType[]`
 * - `POST /api/rental-requests`          → `RentalRequestCreated`
 *
 * Hai endpoint GET là public (không cần token) và đã có trên main.
 */

import { apiRequest } from "./api";

export type Facility = {
  id: number;
  code: string;
  name: string;
  address: string;
  /** Chuỗi hiển thị, ví dụ "08:00–20:00 hằng ngày". */
  operatingHours: string;
  /** Loại khoang cơ sở cung cấp, sắp tăng dần. */
  unitTypeIds: number[];
};

export type UnitType = {
  id: number;
  name: string;
  /** mét */
  width: number;
  depth: number;
  height: number;
  /** m² */
  area: number;
  /** VND / tháng */
  monthlyPrice: number;
};

export type CreateRentalRequest = {
  facilityId: number;
  customerName: string;
  customerEmail: string;
  /** 7–15 chữ số */
  customerPhone: string;
  unitTypeId: number;
  /** yyyy-MM-dd, không ở quá khứ (giờ Việt Nam) */
  startDate: string;
  /** số tháng, >= 1 */
  period: number;
};

export type RentalRequestCreated = {
  id: number;
  status: "Pending";
  startDate: string;
  expiresAt: string;
};

export function fetchFacilities(): Promise<Facility[]> {
  return apiRequest<Facility[]>("/api/facilities");
}

/** Cơ sở không tồn tại trả mảng rỗng (backend không trả 404). */
export function fetchUnitTypes(facilityId: number): Promise<UnitType[]> {
  return apiRequest<UnitType[]>(`/api/unit-types?facilityId=${facilityId}`);
}

const vnNumber = new Intl.NumberFormat("vi-VN");
const vnOneDecimal = new Intl.NumberFormat("vi-VN", {
  minimumFractionDigits: 1,
  maximumFractionDigits: 2,
});

/** 500000 → "500.000 đ" */
export function formatVnd(value: number): string {
  return `${vnNumber.format(value)} đ`;
}

/** 7.5 → "7,5 m²" */
export function formatArea(area: number): string {
  return `${vnNumber.format(area)} m²`;
}

/** → "2,0 × 2,5 × 2,2 m" (rộng × sâu × cao) */
export function formatDimensions(unit: UnitType): string {
  return `${vnOneDecimal.format(unit.width)} × ${vnOneDecimal.format(unit.depth)} × ${vnOneDecimal.format(unit.height)} m`;
}
