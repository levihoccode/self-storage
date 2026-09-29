package vn.lemar.selfstorage;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/**
 * Chặn việc gọi sai chiều giữa các module ngay từ lúc build.
 *
 * <p>Chiều phụ thuộc chốt ở issue #15: tenancy -> handover -> booking, checkout -> handover
 * và tenancy; mọi module đọc được từ facility, pricing, identity. Nếu ai viết code trong
 * booking mà gọi thẳng sang handover thì test này fail, không cần chờ ai review bắt lỗi.
 *
 * <p>Test chỉ phân tích bytecode, không khởi động Spring context nên chạy được trong CI
 * mà không cần Postgres hay Redis.
 */
class ModuleStructureTest {

    private static final ApplicationModules MODULES = ApplicationModules.of(SelfStorageApplication.class);

    @Test
    void khongCoPhuThuocSaiChieuHoacVongLap() {
        MODULES.verify();
    }

    @Test
    void inSoDoModuleDeDoiChieu() {
        MODULES.forEach(System.out::println);
    }
}
