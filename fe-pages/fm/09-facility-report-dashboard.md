# Báo cáo cơ sở

- **Route:** `/fm/reports`
- **Actor:** Facility Manager
- **Flow tham chiếu:** 5.4 (branch `specs/flow-5`)

## Mục đích
FM theo dõi tình hình vận hành của riêng cơ sở mình phụ trách.

## Navigation

- **Vào từ:** FM shell.
- **Đi tới:** `/fm/storage-units`, `/fm/contracts`, `/fm/invoices`, `/fm/support-requests` từ các KPI hoặc drill-down.

## Dữ liệu hiển thị
- Số khoang trống/đã thuê, tỷ lệ lấp đầy
- Doanh thu (phụ thuộc chính sách giá từ Flow 4)
- Số ca quá hạn tại cơ sở (phụ thuộc dữ liệu Flow 6)
- Filter theo thời gian và loại khoang (`unit_type`)

## Actions
- Chọn filter (khoảng thời gian, loại khoang) → refetch số liệu
- [Xuất báo cáo] (nếu cần, không thấy chốt trong tài liệu — đề xuất, cần xác nhận có trong MVP không)

## States / UI trạng thái
- Loading khi tính toán số liệu
- Biểu đồ (cột/đường/tròn) hoặc bảng số liệu — tương tự `bom/04-revenue-dashboard.md` nhưng scope giới hạn 1 facility

## API liên quan
- `GET /api/fm/reports?from=&to=&unit_type_id=`

## Edge case / Lưu ý UX
- Truy vấn report **bắt buộc lọc theo facility mà `Facility.fm_account_id` trỏ tới FM đang đăng nhập ở tầng API** (không chỉ ẩn ở FE) — tránh rủi ro IDOR nếu FM đổi param trên URL/request
- Report toàn hệ thống/so sánh nhiều cơ sở **không** thuộc trang này — đó là quyền của BOM (`bom/08-system-wide-report.md`)
