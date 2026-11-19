package vn.lemar.selfstorage.handover.application;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;
import vn.lemar.selfstorage.handover.application.exception.HandoverConflictException;
import vn.lemar.selfstorage.handover.application.exception.HandoverNotFoundException;
import vn.lemar.selfstorage.handover.domain.RentalContract;
import vn.lemar.selfstorage.handover.repository.HandoverReadRepository;
import vn.lemar.selfstorage.handover.repository.HandoverReadRepository.ContractDocumentRow;
import vn.lemar.selfstorage.identity.application.Access;
import vn.lemar.selfstorage.identity.application.CurrentAccountProvider;
import vn.lemar.selfstorage.pricing.application.PricingQuery;

/**
 * Spec 2.3: render tài liệu hợp đồng từ mẫu đang hiệu lực + dữ liệu phiên. Chưa có bảng mẫu (Flow 4) — dùng mẫu
 * dựng sẵn {@code builtin-v1}; {@code document_url} trỏ về endpoint này.
 *
 * <p>Trước khi ký: render động từ dữ liệu hiện tại (bản nháp để xem/in). Lúc FS upload bản đã ký, workflow render
 * một lần ({@link #html}) và chốt vào {@code rental_contracts.document_html}; từ đó endpoint trả đúng bản đã chốt.
 * Còn đề nghị đổi mốc tính tiền {@code Pending} → không render (409).
 */
@Service
public class ContractDocumentService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final HandoverReadRepository readRepository;
    private final Access access;
    private final CurrentAccountProvider currentAccount;
    private final PricingQuery pricingQuery;

    ContractDocumentService(HandoverReadRepository readRepository, Access access,
                            CurrentAccountProvider currentAccount, PricingQuery pricingQuery) {
        this.readRepository = readRepository;
        this.access = access;
        this.currentAccount = currentAccount;
        this.pricingQuery = pricingQuery;
    }

    /** Mốc và phí xử lý quá hạn kéo dài in vào Điều 6 — đọc từ policy lúc render / lúc chốt bản ký. */
    public record OverdueTerms(int graceDays, BigDecimal feePerDay, int escalationDays, int abandonDays,
                               int holdDays, BigDecimal reclaimFee, BigDecimal holdFeePerDay) {

        public static OverdueTerms from(PricingQuery pricing) {
            return new OverdueTerms(pricing.intPolicy("overdue.grace_days"),
                    pricing.decimalPolicy("overdue.fee_per_day"), pricing.intPolicy("overdue.escalation_days"),
                    pricing.intPolicy("overdue.abandon_days"), pricing.intPolicy("storage.hold_days"),
                    pricing.decimalPolicy("fee.reclaim"), pricing.decimalPolicy("fee.hold_per_day"));
        }
    }

    /** Khách của hợp đồng, hoặc FS/FM của cơ sở ({@code rental_order.read}). */
    @Transactional(readOnly = true)
    public String render(Long contractId) {
        ContractDocumentRow row = readRepository.findContractDocument(contractId)
                .orElseThrow(() -> new HandoverNotFoundException("Không tìm thấy hợp đồng"));
        if (!row.customerId().equals(currentAccount.getCurrentAccountId())) {
            access.canGlobalOrAt(HandoverQueryService.PERMISSION_READ, row.facilityId());
        }
        if (row.documentHtml() != null) {
            return row.documentHtml();
        }
        if (RentalContract.OVERRIDE_PENDING.equals(row.overrideStatus())) {
            throw new HandoverConflictException("Đề nghị đổi mốc tính tiền đang chờ FM duyệt — chưa thể in hợp đồng");
        }
        return html(row, OverdueTerms.from(pricingQuery));
    }

    /** Render mẫu không kèm số liệu policy (Điều 6 ghi "theo chính sách hiện hành"). */
    static String html(ContractDocumentRow row) {
        return html(row, null);
    }

    /** Render mẫu từ dòng đã nạp — không kiểm quyền; gọi được trong transaction ký hợp đồng. */
    static String html(ContractDocumentRow row, OverdueTerms terms) {
        StringBuilder out = new StringBuilder(4096);
        out.append("<!doctype html><html lang=\"vi\"><head><meta charset=\"utf-8\"><title>Hợp đồng ")
                .append(esc(row.code())).append("</title><style>")
                .append("body{font-family:'Times New Roman',serif;max-width:780px;margin:32px auto;padding:0 16px;")
                .append("line-height:1.5;color:#111}h1{text-align:center;font-size:20px}")
                .append("h2{font-size:16px;margin-top:24px}table{border-collapse:collapse;width:100%}")
                .append("td{padding:4px 8px;vertical-align:top}td:first-child{width:38%;color:#444}")
                .append(".sign{display:flex;justify-content:space-between;margin-top:48px;text-align:center}")
                .append(".sign div{width:45%}.meta{font-size:12px;color:#666;text-align:center}")
                .append("@media print{.meta{display:none}}</style></head><body>")
                .append("<p class=\"meta\">Mã hợp đồng ").append(esc(row.code())).append(" · Mẫu ")
                .append(esc(row.termsVersion())).append(" · Trạng thái ").append(esc(row.status())).append("</p>")
                .append("<h1>HỢP ĐỒNG THUÊ KHOANG LƯU TRỮ</h1>")
                .append("<h2>Điều 1. Các bên</h2><table>")
                .append(tr("Bên cho thuê", "Kho Mộc — " + row.facilityName()))
                .append(tr("Địa chỉ cơ sở", row.facilityAddress()))
                .append(tr("Bên thuê", row.customerName()))
                .append(tr("Email", row.customerEmail()))
                .append(tr("Số điện thoại", row.customerPhone()))
                .append("</table><h2>Điều 2. Đối tượng thuê</h2><table>")
                .append(tr("Khoang chứa", row.unitCode() + " (" + row.unitType() + ")"))
                .append(tr("Diện tích", row.unitArea() == null ? null : row.unitArea().stripTrailingZeros()
                        .toPlainString() + " m²"))
                .append(tr("Hiện trạng khi bàn giao", row.inspectionNotes() == null ? "Theo biên bản bàn giao"
                        : row.inspectionNotes()))
                .append("</table><h2>Điều 3. Thời hạn và giá thuê</h2><table>")
                .append(tr("Ngày bắt đầu tính tiền", date(row.startDate())))
                .append(tr("Ngày kết thúc", date(row.endDate())))
                .append(tr("Thời hạn", row.period() + " tháng"))
                .append(tr("Giá thuê", money(row.monthlyPrice()) + " / tháng"))
                .append(tr("Tiền cọc đã đóng", money(row.depositAmount())))
                .append("</table><h2>Điều 4. Thanh toán</h2>")
                .append("<p>Bên thuê thanh toán tiền thuê tháng đầu trước khi nhận bàn giao khoang. Tiền cọc được ")
                .append("giữ riêng, không trừ vào tiền thuê và được xử lý khi trả kho theo chính sách của bên ")
                .append("cho thuê.</p>")
                .append("<h2>Điều 5. Quyền và nghĩa vụ</h2><ol>")
                .append("<li>Bên thuê không lưu trữ hàng cấm, chất dễ cháy nổ, thực phẩm tươi sống hoặc động vật.</li>")
                .append("<li>Bên thuê tự bảo quản chìa khóa/mã truy cập; mất chìa phải báo ngay cho cơ sở.</li>")
                .append("<li>Bên cho thuê bảo đảm an ninh khu vực, giữ nguyên hiện trạng khoang như biên bản.</li>")
                .append("<li>Gia hạn, trả kho trước hạn và phí phát sinh theo chính sách hiện hành của cơ sở.</li>")
                .append("</ol>")
                .append(overdueClause(terms))
                .append("<div class=\"sign\"><div><b>BÊN CHO THUÊ</b><br>(ký, ghi rõ họ tên)</div>")
                .append("<div><b>BÊN THUÊ</b><br>(ký, ghi rõ họ tên)<br><br><br>").append(esc(row.customerName()))
                .append("</div></div></body></html>");
        return out.toString();
    }

    /** Điều 6 — xử lý khi quá hạn kéo dài: phí, tạm khoá, thu hồi khoang, lưu giữ và thanh lý đồ. */
    private static String overdueClause(OverdueTerms t) {
        String grace = t == null ? "thời gian ân hạn" : t.graceDays() + " ngày ân hạn";
        String fee = t == null ? "theo chính sách hiện hành" : money(t.feePerDay()) + " / ngày";
        String lock = t == null ? "kéo dài" : "từ " + t.escalationDays() + " ngày";
        String abandon = t == null ? "theo chính sách hiện hành" : "từ " + t.abandonDays() + " ngày";
        String hold = t == null ? "theo chính sách hiện hành" : t.holdDays() + " ngày";
        String reclaimFee = t == null ? "phí thu hồi" : "phí thu hồi " + money(t.reclaimFee());
        String holdFee = t == null ? "phí lưu giữ" : "phí lưu giữ " + money(t.holdFeePerDay()) + " / ngày";
        return "<h2>Điều 6. Xử lý khi quá hạn kéo dài</h2><ol>"
                + "<li>Quá ngày kết thúc mà chưa gia hạn hoặc trả kho, sau " + esc(grace) + " Bên thuê chịu phí quá "
                + "hạn " + esc(fee) + ".</li>"
                + "<li>Quá hạn " + esc(lock) + ", Bên cho thuê tạm khoá quyền truy cập (thu hồi mã số; khoang dùng "
                + "chìa được gắn khoá chặn của cơ sở) cho tới khi Bên thuê thanh toán phí và gia hạn, hoặc đến trả "
                + "kho và thanh toán hết nợ.</li>"
                + "<li>Quá hạn " + esc(abandon) + " mà Bên thuê không xử lý, Bên cho thuê được thu hồi khoang: mở "
                + "khoang có nhân viên chứng kiến, lập biên bản kiểm kê kèm ảnh, chuyển tài sản về khu lưu giữ của "
                + "cơ sở; hợp đồng chấm dứt, tiền cọc được trừ vào công nợ, " + esc(reclaimFee) + " và phí mất chìa "
                + "(nếu có).</li>"
                + "<li>Tài sản được lưu giữ " + esc(hold) + " kể từ ngày thu hồi, " + esc(holdFee) + ". Bên thuê "
                + "nhận lại tài sản sau khi thanh toán hết công nợ và phí lưu giữ.</li>"
                + "<li>Hết thời hạn lưu giữ mà Bên thuê không nhận lại, Bên cho thuê được thanh lý tài sản để trừ "
                + "công nợ: lập biên bản thanh lý; giấy tờ cá nhân không bán mà gửi trả Bên thuê hoặc bàn giao cơ "
                + "quan có thẩm quyền; tiền thanh lý còn dư sau khi trừ nợ được hoàn cho Bên thuê, còn thiếu thì "
                + "Bên thuê tiếp tục chịu công nợ.</li></ol>";
    }

    private static String tr(String label, String value) {
        return "<tr><td>" + esc(label) + "</td><td>" + esc(value == null ? "—" : value) + "</td></tr>";
    }

    private static String esc(String value) {
        // UTF-8: chỉ escape ký tự đặc biệt HTML, giữ nguyên tiếng Việt.
        return value == null ? "" : HtmlUtils.htmlEscape(value, "UTF-8");
    }

    private static String date(LocalDate date) {
        return date == null ? null : DATE.format(date);
    }

    private static String money(BigDecimal amount) {
        if (amount == null) {
            return "—";
        }
        return NumberFormat.getNumberInstance(Locale.forLanguageTag("vi-VN")).format(amount) + " đ";
    }
}
