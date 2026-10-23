package vn.lemar.selfstorage.pricing.application;

import java.math.BigDecimal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.lemar.selfstorage.pricing.repository.PricingRepository;

/**
 * Public API cho module nghiệp vụ: tham số chính sách dạng số nguyên và giá thuê hiệu lực. Giá trị policy đọc qua
 * {@link PolicyQueryService} — một nơi duy nhất đọc bảng {@code policies}.
 */
@Service
public class PricingQuery {

    private final PolicyQueryService policyQueryService;
    private final PricingRepository pricingRepository;

    public PricingQuery(PolicyQueryService policyQueryService, PricingRepository pricingRepository) {
        this.policyQueryService = policyQueryService;
        this.pricingRepository = pricingRepository;
    }

    /**
     * Tham số số nguyên theo key (vd {@code invoice.deposit_due_days}). Thiếu key là lỗi cấu hình seed —
     * ném {@link IllegalStateException} chứ không tự đoán giá trị mặc định.
     */
    @Transactional(readOnly = true)
    public int intPolicy(String key) {
        String value = policyQueryService.valueOf(key)
                .orElseThrow(() -> new IllegalStateException("Thiếu tham số policies." + key));
        return Integer.parseInt(value.strip());
    }

    /** Tham số tiền tệ theo key (vd {@code fee.unit_change}); thiếu key là lỗi cấu hình seed. */
    @Transactional(readOnly = true)
    public BigDecimal decimalPolicy(String key) {
        String value = policyQueryService.valueOf(key)
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
