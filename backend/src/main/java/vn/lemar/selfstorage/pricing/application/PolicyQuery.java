package vn.lemar.selfstorage.pricing.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.lemar.selfstorage.pricing.repository.PolicyRepository;

/**
 * Public API đọc tham số nghiệp vụ trong bảng {@code policies} theo key của spec
 * ({@code specs/db-table-draft.md} › Policy). Giá trị lưu dạng chuỗi, nơi đọc tự ép kiểu.
 */
@Service
public class PolicyQuery {

    private final PolicyRepository policyRepository;

    public PolicyQuery(PolicyRepository policyRepository) {
        this.policyRepository = policyRepository;
    }

    /**
     * Giá trị số nguyên của {@code key}.
     *
     * @throws IllegalStateException key chưa seed hoặc không phải số nguyên — lỗi cấu hình dữ liệu,
     *                               không có giá trị mặc định thay thế
     */
    @Transactional(readOnly = true)
    public int requireInt(String key) {
        String value = policyRepository.findValue(key)
                .orElseThrow(() -> new IllegalStateException("Thiếu policy '" + key + "' trong bảng policies"));
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Policy '" + key + "' không phải số nguyên: " + value, e);
        }
    }
}
