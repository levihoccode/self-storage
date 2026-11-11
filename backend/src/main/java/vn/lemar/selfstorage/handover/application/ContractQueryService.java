package vn.lemar.selfstorage.handover.application;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.lemar.selfstorage.handover.application.dto.ContractSummaryResponse;
import vn.lemar.selfstorage.handover.repository.RentalContractReadRepository;
import vn.lemar.selfstorage.pricing.application.PricingQuery;

/** Đọc hợp đồng của chính khách đang đăng nhập — màn "Kho của tôi". */
@Service
public class ContractQueryService {

    /** Ngày nghiệp vụ tính theo giờ Việt Nam, giống luồng tạo yêu cầu thuê. */
    static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    /** Cửa sổ cảnh báo "sắp hết hạn", seed V22; BOM đổi bằng UPDATE policies. */
    static final String POLICY_EXPIRING_SOON_DAYS = "contract.expiring_soon_days";

    private final RentalContractReadRepository readRepository;
    private final PricingQuery pricingQuery;

    public ContractQueryService(RentalContractReadRepository readRepository, PricingQuery pricingQuery) {
        this.readRepository = readRepository;
        this.pricingQuery = pricingQuery;
    }

    @Transactional(readOnly = true)
    public List<ContractSummaryResponse> listForCustomer(Long customerId) {
        List<RentalContractReadRepository.ContractRow> rows = readRepository.findActiveByCustomer(customerId);
        if (rows.isEmpty()) {
            return List.of();
        }
        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        int expiringSoonDays = pricingQuery.intPolicy(POLICY_EXPIRING_SOON_DAYS);
        return rows.stream()
                .map(row -> ContractSummaryResponse.from(row, today, expiringSoonDays))
                .toList();
    }
}
