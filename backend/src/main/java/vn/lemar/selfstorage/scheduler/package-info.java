/**
 * Cron jobs dùng chung, chạy kèm ShedLock để không trùng job khi multi-instance (issue #15 mục 5).
 *
 * <p>Module này gọi sang các module nghiệp vụ nên để mở chiều phụ thuộc; issue #15 chưa
 * chốt cụ thể, cần thống nhất trước khi viết job thật.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Scheduler")
package vn.lemar.selfstorage.scheduler;
