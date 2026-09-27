# Dashboard doanh thu

- **Route:** `/bom/revenue`
- **Actor:** Business Operation Manager
- **Flow tham chiếu:** 4.3 (branch `specs/flow-4`)

## Mục đích
BOM theo dõi doanh thu toàn chuỗi, tổng hợp dữ liệu cho thuê theo thời gian/chi nhánh/loại kho/khách hàng để đánh giá và so sánh.

## Navigation

- **Vào từ:** BOM shell sau login.
- **Đi tới:** `/bom/reports`, `/bom/facilities`, `/bom/unit-types`, `/bom/fees` từ các drill-down; export nếu được chốt.

## Dữ liệu hiển thị
- Doanh thu mặc định 2 tháng gần nhất khi vào trang
- Dữ liệu tổng hợp từ `Contract`, `Fee`, `Payment`, `Rental History`
- Dạng bảng hoặc biểu đồ (cột/đường/tròn)
- Mô hình so sánh: chọn 1 biểu đồ cần so sánh, phần bên phải hiển thị so sánh theo từng thay đổi (data trả về cùng lúc với biểu đồ chính)

## Actions
- Chọn tiêu chí lọc: thời gian, chi nhánh, loại kho, khách hàng — **có thể chọn nhiều đơn vị lọc cùng lúc**, hệ thống tính toán lại khi thay đổi
- [Bật/tắt chế độ so sánh]

## States / UI trạng thái
- Loading khi tính toán lại theo filter
- Empty state nếu chưa có dữ liệu trong khoảng thời gian chọn

## API liên quan
- `GET /api/bom/revenue?from=&to=&facility_id=&unit_type_id=&customer_id=`

## Edge case / Lưu ý UX
- Đây là dashboard **toàn hệ thống** (mọi chi nhánh) — khác với `fm/09-facility-report-dashboard.md` chỉ giới hạn 1 facility của FM đang đăng nhập
- Tài liệu flow-4 chưa chốt rõ "Schema liên quan" cho phần này (để trống trong draft) — cần xác nhận cụ thể field trả về từ BE trước khi code chart chi tiết
