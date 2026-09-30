/**
 * Flow 3 - quáº£n lÃ½ kho Ä‘ang thuÃª: gia háº¡n, yÃªu cáº§u tráº£ kho, bÃ¡o sá»± cá»‘.
 *
 * <p>Chiá»u phá»¥ thuá»™c theo issue #15: tenancy -> handover -> booking.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Tenancy (F3)",
        allowedDependencies = {"handover", "booking", "facility", "pricing", "identity", "payment", "notification"})
package vn.lemar.selfstorage.tenancy;
