package vn.lemar.selfstorage.pricing.application;

import java.math.BigDecimal;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.lemar.selfstorage.pricing.repository.PricingRepository;

/**
 * Public API cho module nghiệp vụ: đọc policy thô, tham số chính sách dạng số và giá thuê hiệu lực.
 * Một nơi duy nhất đọc bảng {@code policies}.
 */
@Service
public class PricingQuery {

    private final PricingRepository pricingRepository;

    public PricingQuery(PricingRepository pricingRepository) {
        this.pricingRepository = pricingRepository;
    }

    /** Giá trị policy thô theo key; rỗng nếu key chưa seed. */
    @Transactional(readOnly = true)
    public Optional<String> valueOf(String key) {
        return pricingRepository.findPolicyValue(key);
    }

    /**
     * Tham số số nguyên theo key (vd {@code invoice.deposit_due_days}). Thiếu key là lỗi cấu hình seed —
     * ném {@link IllegalStateException} chứ không tự đoán giá trị mặc định.
     */
    @Transactional(readOnly = true)
    public int intPolicy(String key) {
        String value = valueOf(key)
                .orElseThrow(() -> new IllegalStateException("Thiếu tham số policies." + key));
        return Integer.parseInt(value.strip());
    }

    /** Tham số tiền tệ theo key (vd {@code fee.unit_change}); thiếu key là lỗi cấu hình seed. */
    @Transactional(readOnly = true)
    public BigDecimal decimalPolicy(String key) {
        String value = valueOf(key)
                .orElseThrow(() -> new IllegalStateException("Thiếu tham số policies." + key));
        return new BigDecimal(value.strip());
    }

    /** Giá thuê tháng hiệu lực của khoang: giá riêng của khoang nếu có, không thì giá của loại khoang. */
    @Transactional(readOnly = true)
    public BigDecimal effectiveMonthlyPrice(Long unitId) {
        return pricingRepository.findEffectiveMonthlyPrice(unitId)
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy giá cho khoang " + unitId));
    }
}
