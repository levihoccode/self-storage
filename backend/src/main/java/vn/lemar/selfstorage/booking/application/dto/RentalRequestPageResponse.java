package vn.lemar.selfstorage.booking.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * Trang hàng đợi FM (page/size/total theo quy ước notification list).
 */
public record RentalRequestPageResponse(
        @Schema(description = "Các yêu cầu trong trang") List<RentalRequestSummaryResponse> items,
        @Schema(description = "Trang hiện tại (0-based)") int page,
        @Schema(description = "Kích thước trang") int size,
        @Schema(description = "Tổng số bản ghi") long total
) {
}
