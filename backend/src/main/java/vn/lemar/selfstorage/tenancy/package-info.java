/**
 * Flow 3 - quản lý kho đang thuê: gia hạn, yêu cầu trả kho, báo sự cố.
 *
 * <p>Chiều phụ thuộc theo issue #15: tenancy -> handover -> booking.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Tenancy (F3)",
        allowedDependencies = {"handover", "booking", "facility", "pricing", "identity", "payment", "notification"})
package vn.lemar.selfstorage.tenancy;
