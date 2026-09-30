/**
 * Flow 2 - check-in vÃ  bÃ n giao kho táº¡i cÆ¡ sá»Ÿ.
 *
 * <p>Chiá»u phá»¥ thuá»™c theo issue #15: handover Ä‘á»c Ä‘Æ°á»£c tá»« booking, khÃ´ng cÃ³ chiá»u ngÆ°á»£c láº¡i.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Handover (F2)",
        allowedDependencies = {"booking", "facility", "pricing", "identity", "payment", "notification"})
package vn.lemar.selfstorage.handover;
