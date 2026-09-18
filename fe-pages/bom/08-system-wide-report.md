# Báo cáo toàn hệ thống

- **Route:** `/bom/reports`
- **Actor:** Business Operation Manager
- **Flow tham chiếu:** Actor description (draft.md — "Theo dõi báo cáo của tất cả các cơ sở kho"), mở rộng từ 4.3

## Mục đích
Cho phép BOM so sánh hiệu suất/doanh thu **giữa nhiều chi nhánh** cùng lúc — khác với `bom/04-revenue-dashboard.md` vốn tập trung vào số liệu doanh thu tổng, trang này thiên về so sánh chi nhánh với chi nhánh (tỷ lệ lấp đầy, số ca quá hạn, hiệu suất vận hành).

## Dữ liệu hiển thị
- Bảng so sánh nhiều `Facility`: tỷ lệ lấp đầy, doanh thu, số ca quá hạn, số sự cố phát sinh — theo khoảng thời gian chọn
- Biểu đồ xếp hạng chi nhánh (top/bottom performer)

## Actions
- Chọn nhiều chi nhánh để so sánh
- Chọn khoảng thời gian, loại số liệu (doanh thu/lấp đầy/quá hạn)

## States / UI trạng thái
- Loading khi tính toán lại
- Có thể tái sử dụng phần lớn UI component từ `bom/04-revenue-dashboard.md` (cùng cơ chế filter + so sánh)

## API liên quan
- `GET /api/bom/reports/compare?facility_ids=&from=&to=&metric=`

## Edge case / Lưu ý UX
- **Lưu ý:** trang này được suy ra từ mô tả actor tổng quát, chưa có flow nào mô tả chi tiết UI riêng cho việc "so sánh nhiều chi nhánh" (Flow 4.3 chỉ mô tả rõ 1 dashboard doanh thu chung) — cân nhắc gộp chung với `bom/04-revenue-dashboard.md` thành 1 trang có tab "Tổng quan" / "So sánh chi nhánh" thay vì tách 2 route riêng, tùy quyết định thiết kế IA của team
- Số ca quá hạn phụ thuộc dữ liệu Flow 6 (chưa có tài liệu chi tiết) — phần này có thể tạm để trống/placeholder cho tới khi Flow 6 hoàn thiện
