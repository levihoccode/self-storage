package vn.lemar.selfstorage.pricing.application;

import java.math.BigDecimal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.lemar.selfstorage.pricing.repository.PricingRepository;

/** Public API đọc tham số chính sách ({@code policies}) và giá thuê hiệu lực cho module nghiệp vụ. */
@Service
public class PricingQuery {

    private final PricingRepository pricingRepository;

    public PricingQuery(PricingRepository pricingRepository) {
        this.pricingRepository = pricingRepository;
    }

    /**
     * Tham số số nguyên theo key (vd {@code invoice.deposit_due_days}). Thiếu key là lỗi cấu hình seed —
     * ném {@link IllegalStateException} chứ không tự đoán giá trị mặc định.
     */
    @Transactional(readOnly = true)
    public int intPolicy(String key) {
        String value = pricingRepository.findPolicyValue(key)
                .orElseThrow(() -> new IllegalStateException("Thiếu tham số policies." + key));
        return Integer.parseInt(value.strip());
    }

    /** Giá thuê tháng hiệu lực của khoang: giá riêng của khoang nếu có, không thì giá của loại khoang. */
    @Transactional(readOnly = true)
    public BigDecimal effectiveMonthlyPrice(Long unitId) {
        return pricingRepository.findEffectiveMonthlyPrice(unitId)
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy giá cho khoang " + unitId));
    }
}
