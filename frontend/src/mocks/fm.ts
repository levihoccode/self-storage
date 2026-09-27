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

export type FsStaff = {
  id: string;
  name: string;
  phone: string;
};

export const fsStaff: FsStaff[] = [
  { id: "fs-001", name: "Nguyễn Thành Được", phone: "090 111 2233" },
  { id: "fs-002", name: "Trịnh Bảo Ngọc", phone: "091 222 3344" },
  { id: "fs-003", name: "Phan Hữu Nghĩa", phone: "092 333 4455" },
];

export type AppointmentType = "CHECKIN" | "RETURN";
export type AppointmentStatus = "Pending" | "Done" | "Canceled";

export type FmAppointment = {
  id: string;
  type: AppointmentType;
  customerName: string;
  unitCode: string;
  date: string;
  timeSlot: string;
  staffId: string | null;
  status: AppointmentStatus;
};

export const fmAppointments: FmAppointment[] = [
  {
    id: "appt-001",
    type: "CHECKIN",
    customerName: "Hoàng Gia Huy",
    unitCode: "TD-098",
    date: "28/09/2026",
    timeSlot: "08:00 – 10:00",
    staffId: null,
    status: "Pending",
  },
  {
    id: "appt-002",
    type: "CHECKIN",
    customerName: "Lê Thị Ngọc Hà",
    unitCode: "TD-101",
    date: "28/09/2026",
    timeSlot: "13:00 – 15:00",
    staffId: "fs-001",
    status: "Pending",
  },
  {
    id: "appt-003",
    type: "RETURN",
    customerName: "Nguyễn Minh Anh",
    unitCode: "A-208",
    date: "28/09/2026",
    timeSlot: "17:00 – 19:00",
    staffId: "fs-002",
    status: "Pending",
  },
  {
    id: "appt-004",
    type: "CHECKIN",
    customerName: "Đặng Thu Hằng",
    unitCode: "TD-101",
    date: "29/09/2026",
    timeSlot: "08:00 – 10:00",
    staffId: null,
    status: "Pending",
  },
  {
    id: "appt-005",
    type: "RETURN",
    customerName: "Vũ Anh Tuấn",
    unitCode: "TD-101",
    date: "27/09/2026",
    timeSlot: "13:00 – 15:00",
    staffId: null,
    status: "Pending",
  },
];

export type ReturnRequestStatus = "Pending" | "Assigned";

export type ReturnRequest = {
  id: string;
  customerName: string;
  unitCode: string;
  preferredDate: string;
  reason?: string;
  status: ReturnRequestStatus;
  staffId: string | null;
};

export const returnRequests: ReturnRequest[] = [
  {
    id: "return-501",
    customerName: "Đặng Thu Hằng",
    unitCode: "TD-101",
    preferredDate: "29/09/2026",
    reason: "Đã chuyển hết hàng, muốn trả kho sớm hơn dự kiến trong hợp đồng.",
    status: "Pending",
    staffId: null,
  },
  {
    id: "return-502",
    customerName: "Phan Gia Bảo",
    unitCode: "TD-205",
    preferredDate: "30/09/2026",
    status: "Pending",
    staffId: null,
  },
  {
    id: "return-495",
    customerName: "Nguyễn Minh Anh",
    unitCode: "A-208",
    preferredDate: "27/09/2026",
    reason: "Hết nhu cầu lưu trữ, không gia hạn thêm.",
    status: "Assigned",
    staffId: "fs-002",
  },
];

/** Flow 2.5.1: khi FM phân công FS cho yêu cầu trả kho, hệ thống tự tạo
 * Appointment(type=RETURN) — FM không thao tác gì thêm ở trang lịch hẹn. */
export function recordReturnAppointment(request: ReturnRequest, staffId: string) {
  fmAppointments.push({
    id: `appt-${request.id}`,
    type: "RETURN",
    customerName: request.customerName,
    unitCode: request.unitCode,
    date: request.preferredDate,
    timeSlot: "Chưa xếp khung giờ cụ thể",
    staffId,
    status: "Pending",
  });
}

export type ExtendRequestStatus = "PendingApproval" | "ApprovedPendingPayment" | "Rejected";

export type ExtendRequest = {
  id: string;
  customerName: string;
  unitCode: string;
  currentEndDate: string;
  monthlyPrice: number;
  extraMonths: number;
  requestedAt: string;
  status: ExtendRequestStatus;
  rejectReason?: string;
};

export type SupportIssueType = "LostKey" | "AccessCode" | "UnitDamage" | "Other";
export type SupportRequestStatus = "Open" | "Assigned";
export type SupportReporterRole = "Customer" | "FS";

export type SupportRequest = {
  id: string;
  issueType: SupportIssueType;
  description: string;
  reporterName: string;
  reporterRole: SupportReporterRole;
  contractId: string | null;
  unitCode: string;
  status: SupportRequestStatus;
  staffId: string | null;
};

export const supportRequests: SupportRequest[] = [
  {
    id: "support-801",
    issueType: "UnitDamage",
    description: "Trần khoang bị dột sau trận mưa lớn, hàng bên trong có nguy cơ ẩm ướt.",
    reporterName: "Lê Thị Ngọc Hà",
    reporterRole: "Customer",
    contractId: "storage-002",
    unitCode: "TD-101",
    status: "Open",
    staffId: null,
  },
  {
    id: "support-802",
    issueType: "LostKey",
    description: "Khách báo mất chìa khoá ổ khoá phụ, cần hỗ trợ mở khoang khẩn.",
    reporterName: "Vũ Anh Tuấn",
    reporterRole: "Customer",
    contractId: "storage-001",
    unitCode: "TD-101",
    status: "Open",
    staffId: null,
  },
  {
    id: "support-803",
    issueType: "UnitDamage",
    description: "FS phát hiện khoang trống bị nứt tường trong lúc kiểm tra định kỳ.",
    reporterName: "Trịnh Bảo Ngọc",
    reporterRole: "FS",
    contractId: null,
    unitCode: "TD-206",
    status: "Open",
    staffId: null,
  },
  {
    id: "support-790",
    issueType: "AccessCode",
    description: "Mã cổng ra vào không hoạt động, khách không vào được khu vực kho.",
    reporterName: "Đặng Thu Hằng",
    reporterRole: "Customer",
    contractId: "storage-003",
    unitCode: "TD-101",
    status: "Assigned",
    staffId: "fs-003",
  },
];

export const extendRequests: ExtendRequest[] = [
  {
    id: "extend-601",
    customerName: "Hoàng Gia Huy",
    unitCode: "TD-098",
    currentEndDate: "05/10/2026",
    monthlyPrice: 1200000,
    extraMonths: 2,
    requestedAt: "25/09/2026",
    status: "PendingApproval",
  },
  {
    id: "extend-602",
    customerName: "Lê Thị Ngọc Hà",
    unitCode: "TD-101",
    currentEndDate: "01/10/2026",
    monthlyPrice: 980000,
    extraMonths: 1,
    requestedAt: "26/09/2026",
    status: "PendingApproval",
  },
  {
    id: "extend-588",
    customerName: "Vũ Anh Tuấn",
    unitCode: "TD-101",
    currentEndDate: "10/09/2026",
    monthlyPrice: 980000,
    extraMonths: 3,
    requestedAt: "12/09/2026",
    status: "ApprovedPendingPayment",
  },
];
