# Đăng nhập

- **Route:** `/login`
- **Actor:** Mọi role (Customer, FM, FS, BOM, Admin)
- **Flow tham chiếu:** Không có tài liệu chi tiết riêng (thuộc auth module) — suy ra từ nhu cầu chung của mọi flow ("Khách đăng nhập vào ứng dụng thành công...")

## Mục đích
Cổng vào chung cho mọi loại tài khoản, điều hướng theo `role_id` sau khi đăng nhập thành công.

## Dữ liệu hiển thị / Input form
- Email, mật khẩu
- [Quên mật khẩu]

## Actions
- [Đăng nhập] → xác thực, điều hướng theo role:
  - Customer → `customer/06-my-contracts-dashboard.md`
  - FM → `fm/01-rental-request-queue.md` (hoặc dashboard tổng)
  - FS → `fs/01-daily-schedule.md`
  - BOM → `bom/04-revenue-dashboard.md`
  - Admin → `admin/01-account-management.md`
- Link [Đăng ký] (chỉ hiển thị hướng dẫn cho Customer — FM/FS/BOM/Admin không tự đăng ký, tài khoản do Admin tạo)

## States / UI trạng thái
- Lỗi sai email/mật khẩu
- Lỗi tài khoản `status = Locked`/`Inactive` (hiển thị thông báo liên hệ Admin)
- Loading khi đang xác thực

## API liên quan
- `POST /api/auth/login`

## Edge case / Lưu ý UX
- Mọi lần đăng nhập (kể cả thất bại) được ghi vào `LoginHistory` (email, ip_address, user_agent, status, failure_reason) — không cần FE xử lý gì thêm ngoài việc gọi đúng API
- Tài khoản FM/FS/BOM/Admin được Admin tạo sẵn (Flow 5.0), không có luồng tự đăng ký — nên có UI khác biệt rõ ràng cho việc "khách hàng" vs "nhân viên nội bộ" nếu dùng domain/subdomain riêng cho staff portal
