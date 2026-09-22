# Xem kho có sẵn (Landing / Browse)

- **Route:** `/` hoặc `/units` (public, không cần đăng nhập)
- **Actor:** Khách vãng lai / Customer
- **Flow tham chiếu:** Actor description (draft.md, mục Customer) — "Xem thông tin của các kho có sẵn (type, size, available unit, rental price)". Không có mô tả chi tiết ở bất kỳ flow nào, cần bổ sung khi Flow 1 được viết chi tiết hơn phần này.

## Mục đích
Trang giới thiệu để khách vãng lai tìm hiểu dịch vụ trước khi điền form yêu cầu đặt kho (1.1). Đóng vai trò landing/marketing + tra cứu nhanh loại kho, giá, chi nhánh.

## Navigation

- **Vào từ:** `/` hoặc public navigation.
- **Đi tới:** `/units` để xem danh sách; `/rental-requests/new` khi khách chọn gửi nhu cầu.
- **Surface phụ:** Unit details mở bằng modal trên page.

## Dữ liệu hiển thị
- Danh sách `Facility` (chi nhánh) đang `Active`: tên, địa chỉ, giờ hoạt động
- Danh sách `UnitType`: tên loại (Small/Medium/Large), kích thước (width/depth/height/area), `monthly_price`, mô tả
- Không hiển thị số lượng khoang trống theo real-time (chưa chốt) — tránh lộ thông tin tồn kho chi tiết cho đối thủ

## Actions
- [Xem chi tiết loại kho] — mở modal mô tả
- [Gửi yêu cầu đặt kho] → điều hướng sang `customer/02-rental-request-form.md`
- Filter theo chi nhánh / loại kho

## States / UI trạng thái
- Loading danh sách chi nhánh/loại kho
- Empty state nếu chưa có `Facility.status = Active` nào

## API liên quan
- `GET /api/public/facilities` (chỉ trả facility `Active`)
- `GET /api/public/unit-types`

## Edge case / Lưu ý UX
- Không cần đăng nhập để xem trang này (theo draft.md 1.1: "khách hàng điền form không cần đăng nhập")
- Giá hiển thị là `UnitType.monthly_price` hiện tại — có thể khác giá đã chốt trong hợp đồng cũ của khách khác, cần ghi chú nhỏ "giá có thể thay đổi theo thời điểm ký hợp đồng"
