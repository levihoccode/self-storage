/**
 * Catalog công khai (cơ sở + loại khoang). Hình dạng khớp đúng contract đã implement:
 * - `GET /api/facilities`                → `Facility[]`
 * - `GET /api/unit-types?facilityId={id}` → `UnitType[]`
 * - `POST /api/rental-requests`          → `RentalRequestCreated`
 *
 * Hiện dữ liệu là mock (lấy từ ví dụ trong backend/docs/routes). Khi nối backend chỉ thay thân hai hàm
 * `fetchFacilities` / `fetchUnitTypes` bằng `apiRequest`, giao diện không đổi.
 */

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

const MOCK_FACILITIES: Facility[] = [
  {
    id: 1,
    code: "Q7",
    name: "Self Storage Quận 7",
    address: "123 Nguyễn Văn Linh, Quận 7, TP.HCM",
    operatingHours: "08:00–20:00 hằng ngày",
    unitTypeIds: [1, 2, 3],
  },
  {
    id: 2,
    code: "TD",
    name: "Self Storage Thủ Đức",
    address: "456 Võ Văn Ngân, Thủ Đức, TP.HCM",
    operatingHours: "08:00–20:00 hằng ngày",
    unitTypeIds: [1, 2],
  },
];

const MOCK_UNIT_TYPES: UnitType[] = [
  { id: 1, name: "Small", width: 1.0, depth: 2.0, height: 2.2, area: 2.0, monthlyPrice: 500000 },
  { id: 2, name: "Medium", width: 2.0, depth: 2.5, height: 2.2, area: 5.0, monthlyPrice: 900000 },
  { id: 3, name: "Large", width: 2.5, depth: 3.0, height: 2.5, area: 7.5, monthlyPrice: 1400000 },
];

export async function fetchFacilities(): Promise<Facility[]> {
  return MOCK_FACILITIES;
}

/** Cơ sở không tồn tại trả mảng rỗng, giống backend (không 404). */
export async function fetchUnitTypes(facilityId: number): Promise<UnitType[]> {
  const facility = MOCK_FACILITIES.find((item) => item.id === facilityId);
  return MOCK_UNIT_TYPES.filter((unit) => facility?.unitTypeIds.includes(unit.id));
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
