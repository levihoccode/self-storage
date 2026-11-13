// Mỗi flow là một "cảnh": danh sách cửa sổ, mỗi cửa sổ một role và một route.
// Cửa sổ được chia tự động theo màn hình thật (demo/lib/layout.mjs).
// Muốn ghim vị trí — ví dụ rải ra 3 màn hình — thêm `at: [x, y], size: [w, h]` vào slot đó.
//
// Route phải là màn hình thật của FE (frontend/src/app/routes.ts). Màn có id thì để màn danh sách,
// người demo bấm vào dòng — một cú click, không phải gõ.
export const SCENES = {
  1: [
    { email: "customer1@lemar.vn", route: "/units" },
  ],
  2: [
    { email: "customer1@lemar.vn", route: "/my-storage" },
    { email: "fm1@lemar.vn", route: "/fm/appointments" },
    { email: "fs1@lemar.vn", route: "/staff/handovers" },
  ],
  3: [
    { email: "customer1@lemar.vn", route: "/my-storage" },
  ],
  4: [
    { email: "bom1@lemar.vn", route: "/bom/pricing" },
  ],
  5: [
    { email: "bom1@lemar.vn", route: "/bom/facilities" },
    { email: "fm1@lemar.vn", route: "/fm/storage-units" },
  ],
  6: [
    { email: "customer7@lemar.vn", route: "/my-storage" },
    { email: "fm1@lemar.vn", route: "/fm/extend-requests" },
  ],
  7: [
    { email: "customer1@lemar.vn", route: "/support" },
    { email: "fs1@lemar.vn", route: "/staff/support" },
    { email: "fm1@lemar.vn", route: "/fm/support-requests" },
  ],
};

/** Câu nói khi mở cảnh — in ra terminal để không phải nhớ. */
export const TALKING_POINTS = {
  1: ["Khách xem cơ sở, loại khoang, giá và khoang trống", "Đặt kho → chọn ngày bắt đầu và kỳ thuê", "Trả cọc để giữ khoang"],
  2: ["FM thấy lịch hẹn hôm nay và phân công FS", "FS xác nhận khách đến — chỉ trong 15 phút trước giờ hẹn", "FS bàn giao: giấy tờ, chìa khoá, mã truy cập"],
  3: ["Khách xem kho đang thuê, hợp đồng, hoá đơn", "Gửi yêu cầu trả kho", "Theo dõi trạng thái quyết toán cọc"],
  4: ["BOM xem và sửa bảng giá theo loại khoang", "Chính sách cọc, gia hạn, quá hạn", "Báo cáo doanh thu và tỉ lệ lấp đầy"],
  5: ["BOM quản lý danh sách cơ sở", "FM thêm khoang, sửa giá, đặt bảo trì", "Phân công FS theo ca"],
  6: ["Hợp đồng còn 3 ngày → khách gia hạn", "Quá hạn 10 ngày → phí INV-OVD", "Quá hạn 35 ngày → tạm khoá mã truy cập"],
  7: ["Khách gửi phiếu: mất chìa, mã khoá, khiếu nại hoá đơn", "FS nhận phiếu được phân công và xử lý", "FM theo dõi hàng đợi, gán FS, xem phí phát sinh"],
};

export const FLOW_NAMES = {
  1: "Reservation",
  2: "Check-in & handover",
  3: "Quản lý khoang thuê",
  4: "Rules / fee / revenue",
  5: "Facility & staff",
  6: "Gia hạn & quá hạn",
  7: "Support",
};
