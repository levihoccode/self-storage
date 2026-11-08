package vn.lemar.selfstorage.booking.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.lemar.selfstorage.ApiEnvelope;
import vn.lemar.selfstorage.booking.application.AdminInvoicePageView;
import vn.lemar.selfstorage.booking.application.InvoiceListQueryService;

/** Danh sách toàn bộ hoá đơn cho ADMIN; read-only. */
@RestController
@RequestMapping("/api/admin/invoices")
public class AdminInvoiceController {

    private final InvoiceListQueryService invoices;

    public AdminInvoiceController(InvoiceListQueryService invoices) {
        this.invoices = invoices;
    }

    @Operation(summary = "Hoá đơn toàn hệ thống",
            description = "Một trang hoá đơn mới nhất trước; read-only. Dùng cho tab Hoá đơn của admin.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lấy danh sách thành công"),
            @ApiResponse(responseCode = "400", description = "page/size không hợp lệ"),
            @ApiResponse(responseCode = "403", description = "Không phải ADMIN")
    })
    @GetMapping
    public ResponseEntity<ApiEnvelope<AdminInvoicePageView>> list(
            @Parameter(description = "Trang, bắt đầu từ 0") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Số dòng mỗi trang, tối đa 100")
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiEnvelope.ok("Lấy danh sách hoá đơn thành công",
                invoices.listAllForAdmin(page, size)));
    }
}
