export type ProposalStatus = "pending" | "expired";

export type Proposal = {
  id: string;
  unitCode: string;
  unitType: string;
  facility: string;
  district: string;
  size: string;
  monthlyPrice: string;
  proposedAt: string;
  expiresAt: string;
  status: ProposalStatus;
  /** How many times the customer already rejected a proposal on this RentalOrder. */
  rejectionCount: number;
  /** Demo-only: shows the 409 "unit already deposited" banner when the customer agrees. */
  simulateConflict?: boolean;
};

export const MAX_REJECTIONS = 3;

export const proposals: Proposal[] = [
  {
    id: "proposal-101",
    unitCode: "C-112",
    unitType: "Kho tiêu chuẩn",
    facility: "Kho Mộc — Nguyễn Văn Linh",
    district: "Quận 7, TP. Hồ Chí Minh",
    size: "4 m²",
    monthlyPrice: "980.000đ / tháng",
    proposedAt: "02/10/2026",
    expiresAt: "09/10/2026",
    status: "pending",
    rejectionCount: 0,
  },
  {
    id: "proposal-102",
    unitCode: "D-045",
    unitType: "Kho vừa",
    facility: "Kho Mộc — Phạm Văn Đồng",
    district: "Thủ Đức, TP. Hồ Chí Minh",
    size: "8 m²",
    monthlyPrice: "1.680.000đ / tháng",
    proposedAt: "28/09/2026",
    expiresAt: "05/10/2026",
    status: "pending",
    rejectionCount: 2,
    simulateConflict: true,
  },
  {
    id: "proposal-103",
    unitCode: "A-070",
    unitType: "Kho nhỏ",
    facility: "Kho Mộc — Nguyễn Văn Linh",
    district: "Quận 7, TP. Hồ Chí Minh",
    size: "2 m²",
    monthlyPrice: "560.000đ / tháng",
    proposedAt: "12/09/2026",
    expiresAt: "19/09/2026",
    status: "expired",
    rejectionCount: 1,
  },
];
