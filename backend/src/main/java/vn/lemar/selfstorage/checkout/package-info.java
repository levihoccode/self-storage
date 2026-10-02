/**
 * Flow 2.5 - trả kho và bảo trì.
 *
 * <p>Chiều phụ thuộc theo issue #15: checkout -> handover, tenancy. Phần đọc hiện trạng
 * khoang lúc bàn giao lấy qua interface truy vấn của handover, không đụng thẳng repository.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Checkout (F2.5)",
        allowedDependencies = {"handover", "tenancy", "facility", "pricing", "identity", "payment", "notification"})
package vn.lemar.selfstorage.checkout;
