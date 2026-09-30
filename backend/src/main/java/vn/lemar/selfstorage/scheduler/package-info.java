/**
 * Cron jobs dÃ¹ng chung, cháº¡y kÃ¨m ShedLock Ä‘á»ƒ khÃ´ng trÃ¹ng job khi multi-instance (issue #15 má»¥c 5).
 *
 * <p>Module nÃ y gá»i sang cÃ¡c module nghiá»‡p vá»¥ nÃªn Ä‘á»ƒ má»Ÿ chiá»u phá»¥ thuá»™c; issue #15 chÆ°a
 * chá»‘t cá»¥ thá»ƒ, cáº§n thá»‘ng nháº¥t trÆ°á»›c khi viáº¿t job tháº­t.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Scheduler")
package vn.lemar.selfstorage.scheduler;
