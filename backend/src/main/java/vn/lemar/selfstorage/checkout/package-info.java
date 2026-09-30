/**
 * Flow 2.5 - tráº£ kho vÃ  báº£o trÃ¬.
 *
 * <p>Chiá»u phá»¥ thuá»™c theo issue #15: checkout -> handover, tenancy. Pháº§n Ä‘á»c hiá»‡n tráº¡ng
 * khoang lÃºc bÃ n giao láº¥y qua interface truy váº¥n cá»§a handover, khÃ´ng Ä‘á»¥ng tháº³ng repository.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Checkout (F2.5)",
        allowedDependencies = {"handover", "tenancy", "facility", "pricing", "identity", "payment", "notification"})
package vn.lemar.selfstorage.checkout;
