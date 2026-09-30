/**
 * Flow 1 - Ä‘áº·t kho: yÃªu cáº§u thuÃª, Ä‘á» xuáº¥t khoang, Ä‘áº·t cá»c vÃ  chá»n lá»‹ch check-in.
 *
 * <p>Äáº§u chuá»—i phá»¥ thuá»™c: khÃ´ng Ä‘Æ°á»£c gá»i sang handover, checkout hay tenancy.
 * Muá»‘n bÃ¡o cho cÃ¡c module sau thÃ¬ dÃ¹ng domain event (issue #15 má»¥c 1).
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Booking (F1)",
        allowedDependencies = {"facility", "pricing", "identity", "payment", "notification"})
package vn.lemar.selfstorage.booking;
