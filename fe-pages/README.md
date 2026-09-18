# Danh sách Page FE — Self Storage System

Tổng hợp toàn bộ page cần thiết kế cho hệ thống, tổng hợp từ tài liệu nghiệp vụ ở các branch `specs/flow-1`, `specs/flow-2-2.5`, `specs/flow-3`, `specs/flow-4`, `specs/flow-5` (chưa merge vào `main` tại thời điểm viết tài liệu này).

Mỗi page có 1 file riêng, nội dung theo cấu trúc: Route, Actor, Mục đích, Dữ liệu hiển thị, Actions, States, API liên quan, Edge case/UX.

**Lưu ý:** Flow 6 (Xử lý quá hạn/gia hạn) và Flow 7 (Yêu cầu hỗ trợ) chưa có tài liệu phân tích riêng ở bất kỳ branch nào — chỉ có phần cross-reference rải rác trong Flow 3/5. Các page liên quan (queue gia hạn, xử lý sự cố...) được liệt kê dựa trên phần cross-reference đó và cần xác nhận lại khi Flow 6/7 có tài liệu chính thức.

## Customer (`customer/`)
| # | Page | Flow |
|---|---|---|
| 1 | [Xem kho có sẵn](customer/01-browse-units.md) | Pre-Flow 1 (public) |
| 2 | [Form yêu cầu đặt kho](customer/02-rental-request-form.md) | 1.1 |
| 3 | [Kiểm tra kho của tôi (duyệt đề xuất)](customer/03-my-proposals.md) | 1.3 |
| 4 | [Hóa đơn & thanh toán](customer/04-invoices-payment.md) | 1.4 + dùng chung toàn hệ thống |
| 5 | [Chọn lịch hẹn check-in](customer/05-checkin-appointment-booking.md) | 1.5 |
| 6 | [Dashboard kho đang thuê](customer/06-my-contracts-dashboard.md) | 3.1 |
| 7 | [Chi tiết hợp đồng](customer/07-contract-detail.md) | 3.2 |
| 8 | [Panel yêu cầu gia hạn](customer/08-extend-request-panel.md) | 3.3 |
| 9 | [Panel yêu cầu trả kho](customer/09-return-request-panel.md) | 3.4 |
| 10 | [Form báo sự cố](customer/10-support-request-form.md) | 3.5 |

## Shared / Auth (`shared/`)
| # | Page | Ghi chú |
|---|---|---|
| 1 | [Đăng nhập](shared/01-login.md) | Dùng chung mọi role |
| 2 | [Đăng ký & xác minh email](shared/02-register-verify.md) | Flow 1.2 — Customer only |
| 3 | [Trung tâm thông báo](shared/03-notifications-center.md) | Dùng chung mọi role |

## Facility Manager (`fm/`)
| # | Page | Flow |
|---|---|---|
| 1 | [Hàng chờ yêu cầu đặt kho](fm/01-rental-request-queue.md) | 1.1 |
| 2 | [Đề xuất lại khoang](fm/02-proposal-redo.md) | 1.3 |
| 3 | [Lịch hẹn & phân công FS](fm/03-appointment-schedule.md) | 2.1 / 5.3 |
| 4 | [Hàng chờ yêu cầu trả kho](fm/04-return-request-queue.md) | 3.4 |
| 5 | [Hàng chờ yêu cầu gia hạn](fm/05-extend-request-queue.md) | 3.3 / Flow 6 |
| 6 | [Hàng chờ yêu cầu hỗ trợ](fm/06-support-request-queue.md) | 3.5 / Flow 7 |
| 7 | [Quản lý khoang chứa](fm/07-storage-unit-management.md) | 5.2 |
| 8 | [Danh sách nhân viên (FS)](fm/08-staff-list.md) | 5.3 |
| 9 | [Báo cáo cơ sở](fm/09-facility-report-dashboard.md) | 5.4 |
| 10 | [Quản lý hóa đơn cơ sở](fm/10-invoice-management.md) | Cross-flow |
| 11 | [Theo dõi khách hàng & hợp đồng](fm/11-customer-contract-overview.md) | Cross-flow |

## Facility Staff (`fs/`)
| # | Page | Flow |
|---|---|---|
| 1 | [Lịch làm việc trong ngày](fs/01-daily-schedule.md) | 2.1 |
| 2 | [Checklist bàn giao on-site](fs/02-onsite-handover-checklist.md) | 2.2 – 2.4 |
| 3 | [Checklist trả kho on-site](fs/03-onsite-return-checklist.md) | 2.5.2 – 2.5.3 |
| 4 | [Xử lý yêu cầu hỗ trợ được phân công](fs/04-support-request-handling.md) | Flow 7 |
| 5 | [Form tự ghi nhận sự cố](fs/05-incident-report-form.md) | 3.5 (FS tạo) |

## Business Operation Manager (`bom/`)
| # | Page | Flow |
|---|---|---|
| 1 | [Quản lý business rules / chính sách](bom/01-business-rules-policy.md) | 4.1 |
| 2 | [Quản lý các loại phí](bom/02-fee-management.md) | 4.2 |
| 3 | [Quản lý discount](bom/03-discount-management.md) | 4.2 (mở rộng) |
| 4 | [Dashboard doanh thu](bom/04-revenue-dashboard.md) | 4.3 |
| 5 | [Quản lý cơ sở (Facility)](bom/05-facility-management.md) | 5.1 |
| 6 | [Chỉ định nhân sự (Role Request)](bom/06-staff-role-request.md) | 5.1 |
| 7 | [Quản lý loại kho & giá thuê](bom/07-unit-type-pricing.md) | 5.2 (owner giá) |
| 8 | [Báo cáo toàn hệ thống](bom/08-system-wide-report.md) | 4.3 (mở rộng) |

## System Administrator (`admin/`)
| # | Page | Flow |
|---|---|---|
| 1 | [Quản lý tài khoản](admin/01-account-management.md) | 5.0 |
| 2 | [Hàng chờ Account Role Request](admin/02-account-role-request-queue.md) | 5.0 / 5.1 |
| 3 | [Quản lý Role & Permission (RBAC)](admin/03-role-permission-management.md) | 5.0 |
| 4 | [Lịch sử đăng nhập](admin/04-login-history.md) | 5.0 |
| 5 | [Audit Log](admin/05-audit-log.md) | 5.0 / 3.6 |

**Tổng: 42 page** (10 Customer + 3 Shared + 11 FM + 5 FS + 8 BOM + 5 Admin).
