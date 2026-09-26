export type StorageStatus = "active" | "expiring" | "overdue";

export type RentedStorage = {
  id: string;
  unitCode: string;
  facility: string;
  district: string;
  size: string;
  startDate: string;
  endDate: string;
  monthlyPrice: string;
  paymentLabel: string;
  status: StorageStatus;
};

export const rentedStorage: RentedStorage[] = [
  {
    id: "storage-001",
    unitCode: "A-208",
    facility: "Kho Mộc — Nguyễn Văn Linh",
    district: "Quận 7, TP. Hồ Chí Minh",
    size: "6 m²",
    startDate: "12/03/2025",
    endDate: "12/03/2026",
    monthlyPrice: "1.450.000đ / tháng",
    paymentLabel: "Đã thanh toán đến 12/03/2026",
    status: "active",
  },
  {
    id: "storage-002",
    unitCode: "B-014",
    facility: "Kho Mộc — Phạm Văn Đồng",
    district: "Thủ Đức, TP. Hồ Chí Minh",
    size: "3 m²",
    startDate: "28/09/2025",
    endDate: "28/12/2025",
    monthlyPrice: "820.000đ / tháng",
    paymentLabel: "Còn 18 ngày thanh toán",
    status: "expiring",
  },
];
