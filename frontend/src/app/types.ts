export type View =
  | "home"
  | "units"
  | "login"
  | "register"
  | "verify"
  | "request"
  | "my-storage"
  | "contract-detail"
  | "proposals"
  | "invoices"
  | "appointments"
  | "notifications"
  | "fm-rental-requests"
  | "fm-proposal-redo"
  | "fm-appointments"
  | "fm-return-requests"
  | "fm-extend-requests"
  | "fm-support-requests"
  | "fm-storage-units"
  | "fm-staff"
  | "fm-reports"
  | "fm-invoices"
  | "fm-contracts";
export type Navigate = (path: string) => void;
export type NoticeTone = "info" | "success" | "error" | "pending";
export type Notice = { tone: NoticeTone; message: string } | null;

/** Trạng thái `ProposalFeedback` do BE trả (chốt #113: `Canceled` thay `Expired`). */
export type ProposalStatus = "Pending" | "Agreed" | "Rejected" | "Canceled";

/** Một đề xuất khoang trên trang "Kiểm tra kho của tôi" — `GET /api/customer/proposals`. */
export type CustomerProposal = {
  id: number;
  orderId: number;
  status: ProposalStatus;
  note: string | null;
  createdAt: string;
  unitId: number;
  unitCode: string;
  unitSize: string | null;
  unitTypeName: string;
  facilityId: number;
  facilityName: string;
  facilityAddress: string | null;
  monthlyPrice: number;
  rejectionCount: number;
  maxRejectionCount: number;
};

/** Proposal sau một thao tác đồng ý / từ chối / đề xuất lại. */
export type ProposalSnapshot = {
  id: number;
  orderId: number;
  customerId: number;
  unitId: number;
  status: ProposalStatus;
  note: string | null;
  createdAt: string;
};

/** Hoá đơn đặt cọc BE tạo khi khách đồng ý. */
export type DepositInvoice = {
  id: number;
  orderId: number;
  code: string;
  title: string;
  description: string;
  type: string;
  status: string;
  amount: number;
  dueDate: string;
};

export type AgreeProposalResult = { proposal: ProposalSnapshot; invoice: DepositInvoice };
export type RejectProposalResult = { proposal: ProposalSnapshot; orderCanceled: boolean };

/** Chi tiết đơn cho màn "Đề xuất lại khoang" — `GET /api/fm/rental-orders/{id}`. */
export type FmRentalOrder = {
  id: number;
  status: string;
  cancelReason: string | null;
  expiresAt: string | null;
  unitId: number | null;
  facilityId: number;
  customer: { accountId: number; name: string; email: string; phone: string };
  requested: { unitTypeId: number; unitTypeName: string; startDate: string; period: number };
  rejectionCount: number;
  maxRejectionCount: number;
  proposals: {
    id: number;
    unitId: number;
    unitCode: string;
    status: ProposalStatus;
    note: string | null;
    createdAt: string;
  }[];
  availableUnits: {
    id: number;
    code: string;
    size: string | null;
    monthlyPrice: number;
    previouslyRejected: boolean;
  }[];
};
