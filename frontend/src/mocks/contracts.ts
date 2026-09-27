export type ContractAction = "extend" | "return" | "report-issue";

export type ExtendRequestStatus =
  "PendingApproval" | "ApprovedPendingPayment" | "Rejected" | "Expired" | "Completed";

export type ReturnRequestStatus = "Pending" | "Assigned" | "Canceled" | "Completed";

export type SupportRequestStatus = "Open" | "Assigned" | "InProgress" | "Resolved" | "Closed";
export type SupportIssueType = "LostKey" | "AccessCode" | "UnitDamage" | "Other";

export type ExtendRequest = {
  status: ExtendRequestStatus;
  extraMonths: number;
  amount?: number;
  dueDate?: string;
  rejectReason?: string;
  newEndDate?: string;
};

export type ReturnRequest = {
  status: ReturnRequestStatus;
  preferredDate: string;
  reason?: string;
};

export type SupportRequest = {
  id: string;
  issueType: SupportIssueType;
  description: string;
  status: SupportRequestStatus;
  createdAt: string;
};

export type HandoverEvent = {
  id: string;
  label: string;
  date: string;
  note: string;
};

export type Contract = {
  id: string;
  unitCode: string;
  unitType: string;
  facility: string;
  district: string;
  size: string;
  signedAt: string;
  startDate: string;
  endDate: string;
  depositAmount: number;
  monthlyPrice: number;
  pdfUrl?: string;
  isOverdue: boolean;
  isExpiringSoon: boolean;
  availableActions: ContractAction[];
  openExtendRequest: ExtendRequest | null;
  openReturnRequest: ReturnRequest | null;
  supportRequests: SupportRequest[];
  handoverHistory: HandoverEvent[];
};

export const SUPPORT_ISSUE_LABEL: Record<SupportIssueType, string> = {
  LostKey: "Mất chìa khoá",
  AccessCode: "Sự cố mã truy cập",
  UnitDamage: "Khoang bị hư hại",
  Other: "Khác",
};

export const contracts: Record<string, Contract> = {
  "storage-001": {
    id: "storage-001",
    unitCode: "A-208",
    unitType: "Kho linh hoạt",
    facility: "Kho Mộc — Nguyễn Văn Linh",
    district: "Quận 7, TP. Hồ Chí Minh",
    size: "6 m²",
    signedAt: "12/03/2025",
    startDate: "12/03/2025",
    endDate: "12/03/2026",
    depositAmount: 1450000,
    monthlyPrice: 1450000,
    pdfUrl: "#",
    isOverdue: false,
    isExpiringSoon: false,
    availableActions: ["extend", "return", "report-issue"],
    openExtendRequest: null,
    openReturnRequest: null,
    supportRequests: [
      {
        id: "support-001",
        issueType: "AccessCode",
        description: "Mã truy cập cổng phụ không hoạt động vào cuối tuần.",
        status: "Resolved",
        createdAt: "02/08/2025",
      },
    ],
    handoverHistory: [
      {
        id: "handover-001",
        label: "Bàn giao khoang",
        date: "12/03/2025",
        note: "Đã kiểm tra tình trạng khoang, bàn giao chìa khoá.",
      },
    ],
  },
  "storage-002": {
    id: "storage-002",
    unitCode: "B-014",
    unitType: "Kho cơ bản",
    facility: "Kho Mộc — Phạm Văn Đồng",
    district: "Thủ Đức, TP. Hồ Chí Minh",
    size: "3 m²",
    signedAt: "28/09/2025",
    startDate: "28/09/2025",
    endDate: "28/12/2025",
    depositAmount: 820000,
    monthlyPrice: 820000,
    isOverdue: false,
    isExpiringSoon: true,
    availableActions: ["report-issue"],
    openExtendRequest: {
      status: "ApprovedPendingPayment",
      extraMonths: 3,
      amount: 2460000,
      dueDate: "05/10/2026",
    },
    openReturnRequest: null,
    supportRequests: [],
    handoverHistory: [
      {
        id: "handover-002",
        label: "Bàn giao khoang",
        date: "28/09/2025",
        note: "Bàn giao đúng hẹn, không phát sinh vấn đề.",
      },
    ],
  },
};
