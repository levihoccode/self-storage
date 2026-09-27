export type RentalRequestStatus = "Pending" | "Approved" | "Rejected" | "Expired" | "Wishlisted";

export type RentalRequest = {
  id: string;
  customerName: string;
  customerPhone: string;
  customerEmail: string;
  /** false → customer_id IS NULL: request was approved by email before the person registered. */
  hasAccount: boolean;
  unitTypeId: string;
  desiredSize: string;
  note: string;
  createdAt: string;
  status: RentalRequestStatus;
  rejectReason?: string;
  approvedUnitCode?: string;
  /** Demo-only: shows the 409 "unit just taken" banner when the FM approves. */
  simulateConflict?: boolean;
};

export type FmUnit = {
  id: string;
  code: string;
  unitTypeId: string;
  size: string;
  status: "Available" | "Occupied" | "Maintenance";
};

export const FM_FACILITY_NAME = "Kho Mộc — Thảo Điền Hub";

export const fmUnits: FmUnit[] = [
  { id: "unit-fm-101", code: "TD-101", unitTypeId: "basic", size: "4 m²", status: "Available" },
  { id: "unit-fm-102", code: "TD-102", unitTypeId: "basic", size: "4 m²", status: "Available" },
  {
    id: "unit-fm-205",
    code: "TD-205",
    unitTypeId: "flexible",
    size: "8 m²",
    status: "Available",
  },
  { id: "unit-fm-206", code: "TD-206", unitTypeId: "flexible", size: "8 m²", status: "Occupied" },
];

export const rentalRequests: RentalRequest[] = [
  {
    id: "request-901",
    customerName: "Lê Thị Ngọc Hà",
    customerPhone: "090 123 4567",
    customerEmail: "ha.le@example.com",
    hasAccount: true,
    unitTypeId: "basic",
    desiredSize: "Khoảng 4 m²",
    note: "Cần lưu trữ hàng gia dụng trong 6 tháng, ưu tiên tầng trệt.",
    createdAt: "26/09/2026",
    status: "Pending",
  },
  {
    id: "request-902",
    customerName: "Phạm Quốc Bảo",
    customerPhone: "091 234 5678",
    customerEmail: "bao.pham@example.com",
    hasAccount: false,
    unitTypeId: "flexible",
    desiredSize: "Khoảng 8 m²",
    note: "Cần kho cho hàng kinh doanh online, nhập/xuất hàng tuần.",
    createdAt: "25/09/2026",
    status: "Pending",
  },
  {
    id: "request-903",
    customerName: "Đỗ Minh Thư",
    customerPhone: "093 456 7890",
    customerEmail: "thu.do@example.com",
    hasAccount: true,
    unitTypeId: "flexible",
    desiredSize: "Khoảng 8 m²",
    note: "Chuyển nhà tạm thời, cần kho khoảng 3 tháng.",
    createdAt: "24/09/2026",
    status: "Pending",
    simulateConflict: true,
  },
  {
    id: "request-880",
    customerName: "Vũ Anh Tuấn",
    customerPhone: "094 567 8901",
    customerEmail: "tuan.vu@example.com",
    hasAccount: true,
    unitTypeId: "basic",
    desiredSize: "Khoảng 4 m²",
    note: "Lưu trữ tài liệu công ty.",
    createdAt: "18/09/2026",
    status: "Approved",
    approvedUnitCode: "TD-101",
  },
  {
    id: "request-875",
    customerName: "Ngô Bích Trâm",
    customerPhone: "095 678 9012",
    customerEmail: "tram.ngo@example.com",
    hasAccount: true,
    unitTypeId: "extended",
    desiredSize: "Khoảng 15 m²",
    note: "Cần kho lớn nhưng cơ sở không có loại phù hợp.",
    createdAt: "15/09/2026",
    status: "Rejected",
    rejectReason: "Cơ sở hiện không còn khoang mở rộng phù hợp diện tích yêu cầu.",
  },
];

export const MAX_ORDER_REJECTIONS = 3;

export type ProposalFeedbackEntry = {
  unitCode: string;
  note: string;
  rejectedAt: string;
  /** Distinguishes a customer-initiated rejection (1.3) from an on-site
   * rejection at check-in (Flow 2, HandoverRecord.Rejected) — same UI,
   * different trigger, so the FM should be able to tell them apart. */
  source: "customer" | "checkin";
};

export type RentalOrder = {
  id: string;
  customerName: string;
  unitTypeId: string;
  desiredSize: string;
  rejectionHistory: ProposalFeedbackEntry[];
  status: "OpenForRepropose" | "Canceled";
  /** Customer already paid the deposit on a previous proposal — re-proposing
   * now means a fee/deposit difference the FM must see before sending. */
  afterDeposit?: boolean;
  previousDeposit?: number;
};

export const rentalOrders: RentalOrder[] = [
  {
    id: "order-fm-701",
    customerName: "Hoàng Gia Huy",
    unitTypeId: "basic",
    desiredSize: "Khoảng 4 m²",
    rejectionHistory: [
      {
        unitCode: "TD-098",
        note: "Khoang nằm quá xa lối vào, bất tiện khi vận chuyển hàng.",
        rejectedAt: "20/09/2026",
        source: "customer",
      },
    ],
    status: "OpenForRepropose",
  },
  {
    id: "order-fm-702",
    customerName: "Lâm Quốc Việt",
    unitTypeId: "flexible",
    desiredSize: "Khoảng 8 m²",
    rejectionHistory: [
      {
        unitCode: "TD-203",
        note: "Ẩm thấp hơn mong đợi.",
        rejectedAt: "10/09/2026",
        source: "customer",
      },
      {
        unitCode: "TD-207",
        note: "Khách từ chối ngay tại chỗ lúc check-in vì kích thước thực tế nhỏ hơn hình.",
        rejectedAt: "13/09/2026",
        source: "checkin",
      },
      {
        unitCode: "TD-210",
        note: "Không đúng vị trí tầng như yêu cầu.",
        rejectedAt: "16/09/2026",
        source: "customer",
      },
    ],
    status: "Canceled",
  },
  {
    id: "order-fm-703",
    customerName: "Đặng Thu Hằng",
    unitTypeId: "basic",
    desiredSize: "Khoảng 4 m²",
    rejectionHistory: [
      {
        unitCode: "TD-101",
        note: "Khách đã cọc nhưng khoang phát sinh sự cố ẩm mốc, cần đổi khoang khác.",
        rejectedAt: "22/09/2026",
        source: "checkin",
      },
    ],
    status: "OpenForRepropose",
    afterDeposit: true,
    previousDeposit: 980000,
  },
];
