package vn.lemar.selfstorage.checkout.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.lemar.selfstorage.ApiEnvelope;
import vn.lemar.selfstorage.checkout.application.ReclaimService;
import vn.lemar.selfstorage.checkout.application.ReclaimService.GoodsView;
import vn.lemar.selfstorage.checkout.application.ReclaimService.LiquidationInput;
import vn.lemar.selfstorage.checkout.application.ReclaimService.ReclaimBoard;
import vn.lemar.selfstorage.checkout.application.ReclaimService.ReclaimInput;
import vn.lemar.selfstorage.identity.application.CurrentAccountProvider;

/** Thu hồi khoang của hợp đồng quá hạn kéo dài, lưu giữ đồ, khách nhận lại hoặc FM thanh lý. */
@RestController
public class ReclaimController {

    private final ReclaimService reclaimService;
    private final CurrentAccountProvider currentAccount;

    public ReclaimController(ReclaimService reclaimService, CurrentAccountProvider currentAccount) {
        this.reclaimService = reclaimService;
        this.currentAccount = currentAccount;
    }

    @Operation(summary = "FM — chờ thu hồi và đồ đang lưu giữ của cơ sở",
            description = "`candidates`: hợp đồng đang tạm khoá đã tới / sắp tới mốc `overdue.abandon_days` "
                    + "(`eligible` = thu hồi được ngay). `goods`: mọi lô đồ của cơ sở, mới nhất trước. "
                    + "Cần `facility.access`.")
    @GetMapping("/api/fm/reclaims")
    public ResponseEntity<ApiEnvelope<ReclaimBoard>> board(@RequestParam Long facilityId) {
        return ResponseEntity.ok(ApiEnvelope.ok("Thu hồi & lưu giữ", reclaimService.board(facilityId)));
    }

    @Operation(summary = "FM thu hồi khoang",
            description = "Hợp đồng `Active`, quá hạn ≥ `overdue.abandon_days`, đang tạm khoá, không có yêu cầu trả "
                    + "kho mở. Cần FS chứng kiến (đang hoạt động, thuộc cơ sở), biên bản kiểm kê, 1–20 ảnh, vị trí "
                    + "lưu giữ. Kết thúc hợp đồng, chìa cơ ghi mất, khoang bảo trì thủ công; nợ + phí thu hồi + phí "
                    + "mất chìa trừ cọc, phần thiếu thành `INV-DBT`. 400 / 403 / 404 / 409.")
    @PostMapping("/api/fm/contracts/{contractId}/reclaim")
    public ResponseEntity<ApiEnvelope<GoodsView>> reclaim(@PathVariable Long contractId,
                                                          @RequestBody ReclaimRequest body) {
        ReclaimInput input = body == null ? null : new ReclaimInput(body.witnessStaffId(), body.inventoryNote(),
                body.photos(), body.storageLocation());
        return ResponseEntity.ok(ApiEnvelope.ok("Đã thu hồi khoang", reclaimService.reclaim(contractId, input)));
    }

    @Operation(summary = "FM xác nhận đã trả đồ cho khách",
            description = "Lô đồ `Holding`, khách đã yêu cầu nhận lại và đã trả hết `INV-DBT` + `INV-HLD` (còn nợ → "
                    + "409).")
    @PostMapping("/api/fm/reclaimed-goods/{id}/return")
    public ResponseEntity<ApiEnvelope<GoodsView>> confirmReturn(@PathVariable Long id) {
        return ResponseEntity.ok(ApiEnvelope.ok("Đã trả đồ cho khách", reclaimService.confirmReturn(id)));
    }

    @Operation(summary = "FM thanh lý đồ lưu giữ",
            description = "Chỉ sau `hold_until`. Tiền thanh lý (VND nguyên ≥ 0) trừ công nợ + phí lưu giữ; thiếu → "
                    + "`INV-DBT` mới; dư → `surplusAmount` chờ FM hoàn. Cần biên bản và 1–20 ảnh.")
    @PostMapping("/api/fm/reclaimed-goods/{id}/liquidate")
    public ResponseEntity<ApiEnvelope<GoodsView>> liquidate(@PathVariable Long id,
                                                            @RequestBody LiquidationRequest body) {
        LiquidationInput input = body == null ? null
                : new LiquidationInput(body.proceeds(), body.note(), body.photos());
        return ResponseEntity.ok(ApiEnvelope.ok("Đã thanh lý", reclaimService.liquidate(id, input)));
    }

    @Operation(summary = "FM ghi nhận đã hoàn tiền dư thanh lý cho khách")
    @PostMapping("/api/fm/reclaimed-goods/{id}/surplus-refunded")
    public ResponseEntity<ApiEnvelope<GoodsView>> surplusRefunded(@PathVariable Long id) {
        return ResponseEntity.ok(ApiEnvelope.ok("Đã ghi nhận hoàn tiền dư", reclaimService.markSurplusRefunded(id)));
    }

    @Operation(summary = "Khách — đồ lưu giữ của tôi")
    @GetMapping("/api/customer/reclaimed-goods")
    public ResponseEntity<ApiEnvelope<List<GoodsView>>> myGoods() {
        return ResponseEntity.ok(ApiEnvelope.ok("Đồ lưu giữ",
                reclaimService.customerGoods(currentAccount.getCurrentAccountId())));
    }

    @Operation(summary = "Khách yêu cầu nhận lại đồ lưu giữ",
            description = "Lập hoá đơn phí lưu giữ `INV-HLD` (số ngày đã giữ × `fee.hold_per_day`, trừ phần đã trả) "
                    + "nếu chưa có; FM được báo. Khách trả hết `INV-DBT` + `INV-HLD` rồi FM trả đồ.")
    @PostMapping("/api/customer/reclaimed-goods/{id}/return-request")
    public ResponseEntity<ApiEnvelope<GoodsView>> requestReturn(@PathVariable Long id) {
        return ResponseEntity.ok(ApiEnvelope.ok("Đã gửi yêu cầu nhận lại đồ",
                reclaimService.requestReturn(id, currentAccount.getCurrentAccountId())));
    }

    @Schema(description = "Biên bản thu hồi khoang")
    public record ReclaimRequest(Long witnessStaffId, String inventoryNote, List<String> photos,
                                 String storageLocation) {
    }

    @Schema(description = "Biên bản thanh lý")
    public record LiquidationRequest(BigDecimal proceeds, String note, List<String> photos) {
    }
}
