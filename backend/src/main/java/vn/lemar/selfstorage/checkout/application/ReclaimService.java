package vn.lemar.selfstorage.checkout.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.lemar.selfstorage.audit.application.AuditEntry;
import vn.lemar.selfstorage.audit.application.AuditRecorder;
import vn.lemar.selfstorage.audit.domain.AuditAction;
import vn.lemar.selfstorage.audit.domain.AuditEntityType;
import vn.lemar.selfstorage.booking.application.ContractInvoiceService;
import vn.lemar.selfstorage.booking.application.ContractInvoiceService.ContractBill;
import vn.lemar.selfstorage.booking.application.HandoverOrderService;
import vn.lemar.selfstorage.booking.application.InvoiceRefundReasons;
import vn.lemar.selfstorage.booking.application.InvoiceRefundService;
import vn.lemar.selfstorage.booking.application.OrderNotificationService;
import vn.lemar.selfstorage.checkout.domain.ReclaimedGoods;
import vn.lemar.selfstorage.checkout.repository.ReclaimedGoodsRepository;
import vn.lemar.selfstorage.facility.application.StorageUnitService;
import vn.lemar.selfstorage.handover.application.RentalContractService;
import vn.lemar.selfstorage.handover.application.RentalContractService.ContractSnapshot;
import vn.lemar.selfstorage.handover.application.RentalContractService.RevokeResult;
import vn.lemar.selfstorage.handover.application.UnitOverlockService;
import vn.lemar.selfstorage.identity.application.Access;
import vn.lemar.selfstorage.identity.application.CurrentAccountProvider;
import vn.lemar.selfstorage.media.MediaAttachments;
import vn.lemar.selfstorage.notification.application.NotificationType;
import vn.lemar.selfstorage.pricing.application.PricingQuery;
import vn.lemar.selfstorage.tenancy.application.TenancyException;
import vn.lemar.selfstorage.tenancy.application.TenancyService;

/**
 * Flow 6 — hợp đồng quá hạn kéo dài mà khách bỏ đi: FM thu hồi khoang (từ {@code overdue.abandon_days} ngày quá hạn,
 * đang tạm khoá, không có yêu cầu trả kho mở) → đồ lưu giữ {@code storage.hold_days} ngày → khách trả hết công nợ +
 * phí lưu giữ để nhận lại, hoặc hết hạn thì FM thanh lý (tiền thanh lý trừ nợ, dư giữ hộ khách, thiếu thành công nợ).
 *
 * <p>Thu hồi = quyết toán như trả kho: chìa cơ ghi mất, mã thu hồi, khoá chặn đóng; mọi hoá đơn còn nợ + phí thu hồi +
 * phí mất chìa trừ vào cọc; phần thiếu thành một hoá đơn {@code INV-DBT} (không tự hết hạn, chặn khách thuê mới).
 * FM thao tác cần {@code facility.access} tại cơ sở (route {@code /api/fm/**} chỉ role FM).
 */
@Service
public class ReclaimService {

    static final String PERM = "facility.access";
    static final String POLICY_ABANDON = "overdue.abandon_days";
    static final String POLICY_ABANDON_NOTICE = "overdue.abandon_notice_days";
    static final String POLICY_HOLD_DAYS = "storage.hold_days";
    static final String POLICY_LIQUIDATION_NOTICE = "storage.liquidation_notice_days";
    static final String POLICY_RECLAIM_FEE = "fee.reclaim";
    static final String POLICY_HOLD_FEE = "fee.hold_per_day";
    static final String POLICY_LOST_KEY = "fee.lost_key";
    static final int NOTE_MAX = 4000;
    static final int PHOTOS_MAX = 20;
    static final int DEBT_DUE_DAYS = 7;

    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Locale VI = Locale.forLanguageTag("vi-VN");

    /** Hợp đồng quá hạn đang tạm khoá — chờ (hoặc sắp tới) mốc thu hồi. */
    public record ReclaimCandidate(Long contractId, String contractCode, Long orderId, String unitCode,
                                   LocalDate endDate, long daysOverdue, LocalDate reclaimDate, boolean eligible,
                                   boolean returnRequestOpen, BigDecimal outstanding, BigDecimal depositAmount) {
    }

    /** Lô đồ lưu giữ kèm công nợ hiện tại để FM / khách hiển thị. */
    public record GoodsView(Long id, Long contractId, String contractCode, Long orderId, String unitCode,
                            String status, Instant reclaimedAt, String witnessName, String inventoryNote,
                            List<String> photos, String storageLocation, LocalDate holdUntil, long daysHeld,
                            BigDecimal holdFeePerDay, BigDecimal outstandingDebt, Instant returnRequestedAt,
                            boolean returnReady, boolean liquidationDue, Instant returnedAt, Instant liquidatedAt,
                            BigDecimal proceedsAmount, String liquidationNote, List<String> liquidationPhotos,
                            BigDecimal surplusAmount, Instant surplusRefundedAt) {
    }

    public record ReclaimBoard(List<ReclaimCandidate> candidates, List<GoodsView> goods) {
    }

    public record ReclaimInput(Long witnessStaffId, String inventoryNote, List<String> photos,
                               String storageLocation) {
    }

    public record LiquidationInput(BigDecimal proceeds, String note, List<String> photos) {
    }

    private final ReclaimedGoodsRepository goodsRepository;
    private final RentalContractService contracts;
    private final ContractInvoiceService invoices;
    private final HandoverOrderService orders;
    private final TenancyService tenancy;
    private final UnitOverlockService overlocks;
    private final StorageUnitService storageUnits;
    private final OrderNotificationService notifications;
    private final InvoiceRefundService refunds;
    private final PricingQuery pricingQuery;
    private final Access access;
    private final CurrentAccountProvider currentAccount;
    private final AuditRecorder auditRecorder;
    private final MediaAttachments mediaAttachments;
    private final JdbcTemplate jdbc;

    ReclaimService(ReclaimedGoodsRepository goodsRepository, RentalContractService contracts,
                   ContractInvoiceService invoices, HandoverOrderService orders, TenancyService tenancy,
                   UnitOverlockService overlocks, StorageUnitService storageUnits,
                   OrderNotificationService notifications, InvoiceRefundService refunds, PricingQuery pricingQuery,
                   Access access, CurrentAccountProvider currentAccount, AuditRecorder auditRecorder,
                   MediaAttachments mediaAttachments, JdbcTemplate jdbc) {
        this.goodsRepository = goodsRepository;
        this.contracts = contracts;
        this.invoices = invoices;
        this.orders = orders;
        this.tenancy = tenancy;
        this.overlocks = overlocks;
        this.storageUnits = storageUnits;
        this.notifications = notifications;
        this.refunds = refunds;
        this.pricingQuery = pricingQuery;
        this.access = access;
        this.currentAccount = currentAccount;
        this.auditRecorder = auditRecorder;
        this.mediaAttachments = mediaAttachments;
        this.jdbc = jdbc;
    }

    // ------------------------------------------------------------------ đọc

    /**
     * FM — hợp đồng đang tạm khoá đã tới (hoặc sắp tới, trong khoảng cảnh báo cuối) mốc thu hồi, và các lô đồ của cơ
     * sở (mới nhất trước).
     */
    @Transactional(readOnly = true)
    public ReclaimBoard board(Long facilityId) {
        access.can(PERM, facilityId);
        LocalDate today = LocalDate.now(ZONE);
        int abandon = pricingQuery.intPolicy(POLICY_ABANDON);
        int notice = pricingQuery.intPolicy(POLICY_ABANDON_NOTICE);
        List<ReclaimCandidate> candidates = new ArrayList<>();
        for (Long id : contracts.activeEndedBefore(today.minusDays(Math.max(0, abandon - notice) - 1L))) {
            ContractSnapshot c = contracts.find(id).orElse(null);
            if (c == null || !facilityId.equals(c.facilityId()) || !contracts.accessSuspended(id)) {
                continue;
            }
            var open = tenancy.openReturnOf(id);
            if (open.map(r -> r.appointmentArrivedAt() != null).orElse(false)) {
                continue;
            }
            long days = ChronoUnit.DAYS.between(c.endDate(), today);
            candidates.add(new ReclaimCandidate(c.id(), c.code(), c.orderId(), unitCode(c), c.endDate(), days,
                    c.endDate().plusDays(abandon), days >= abandon && open.isEmpty(), open.isPresent(),
                    outstanding(c.id()), c.depositAmount()));
        }
        List<GoodsView> goods = goodsRepository.findByFacilityIdOrderByIdDesc(facilityId).stream().map(this::view)
                .toList();
        return new ReclaimBoard(candidates, goods);
    }

    /** Khách — đồ lưu giữ của các hợp đồng của mình. */
    @Transactional(readOnly = true)
    public List<GoodsView> customerGoods(Long customerId) {
        return jdbc.queryForList("select g.id from reclaimed_goods g join rental_contracts c on c.id = g.contract_id "
                        + "where c.customer_id = ? order by g.id desc", Long.class, customerId).stream()
                .map(id -> view(goodsRepository.findById(id).orElseThrow())).toList();
    }

    // ------------------------------------------------------------------ thu hồi

    /** FM thu hồi khoang của hợp đồng quá hạn kéo dài — quyết toán trừ cọc, đồ chuyển về khu lưu giữ. */
    @Transactional
    public GoodsView reclaim(Long contractId, ReclaimInput input) {
        String note = required(input == null ? null : input.inventoryNote(), "Vui lòng nhập biên bản kiểm kê",
                NOTE_MAX);
        String location = required(input.storageLocation(), "Vui lòng nhập vị trí lưu giữ đồ", 200);
        List<String> photos = photos(input.photos());
        if (input.witnessStaffId() == null) {
            throw TenancyException.invalid("Vui lòng chọn FS chứng kiến");
        }
        ContractSnapshot peek = contracts.find(contractId)
                .orElseThrow(() -> TenancyException.notFound("Không tìm thấy hợp đồng thuê"));
        access.can(PERM, peek.facilityId());
        Long fmId = currentAccount.getCurrentAccountId();
        orders.lock(peek.orderId());
        ContractSnapshot c = contracts.lock(contractId).orElseThrow();
        LocalDate today = LocalDate.now(ZONE);
        int abandon = pricingQuery.intPolicy(POLICY_ABANDON);
        if (!c.active()) {
            throw TenancyException.conflict("Hợp đồng không còn hiệu lực");
        }
        long days = ChronoUnit.DAYS.between(c.endDate(), today);
        if (days < abandon) {
            throw TenancyException.conflict("Chưa tới mốc thu hồi: hợp đồng quá hạn " + Math.max(days, 0)
                    + " ngày, thu hồi được từ ngày " + DATE.format(c.endDate().plusDays(abandon)));
        }
        if (!contracts.accessSuspended(contractId)) {
            throw TenancyException.conflict("Hợp đồng chưa bị tạm khoá truy cập — chưa thu hồi được");
        }
        if (tenancy.openReturnOf(contractId).isPresent()) {
            throw TenancyException.conflict("Khách đang có yêu cầu trả kho — xử lý theo buổi trả kho");
        }
        if (goodsRepository.findByContractId(contractId).isPresent()) {
            throw TenancyException.conflict("Hợp đồng đã được thu hồi");
        }
        requireActiveStaff(input.witnessStaffId(), c.facilityId());

        overlocks.closeOnReclaim(contractId, fmId);
        RevokeResult revoke = contracts.revokeAll(contractId, 0, fmId);
        BigDecimal lostKeys = pricingQuery.decimalPolicy(POLICY_LOST_KEY)
                .multiply(BigDecimal.valueOf(revoke.lostKeys()));
        BigDecimal folded = invoices.foldUnpaidIntoSettlement(contractId, "Gộp vào quyết toán thu hồi khoang");
        BigDecimal reclaimFee = pricingQuery.decimalPolicy(POLICY_RECLAIM_FEE);
        List<Charge> charges = List.of(
                new Charge(ContractInvoiceService.RECLAIM_KIND, "Phí thu hồi khoang", reclaimFee),
                new Charge(ContractInvoiceService.LOST_KEY_KIND, "Phí mất chìa khi thu hồi khoang", lostKeys),
                new Charge(ContractInvoiceService.SETTLEMENT_KIND, "Hoá đơn chưa thanh toán gộp vào quyết toán",
                        folded));
        BigDecimal total = charges.stream().map(Charge::amount).reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal deposit = c.depositAmount();
        BigDecimal remaining = deposit;
        for (Charge charge : charges) {
            BigDecimal covered = charge.amount().setScale(2, RoundingMode.HALF_UP).min(remaining);
            if (covered.signum() > 0) {
                invoices.settleByDeposit(c.orderId(), c.id(), c.customerId(), charge.kind(), covered,
                        charge.title() + " (đối trừ cọc)", fmId);
                remaining = remaining.subtract(covered);
            }
        }
        BigDecimal debt = total.subtract(deposit).max(BigDecimal.ZERO);
        if (debt.signum() > 0) {
            issueDebt(c, debt, "Công nợ sau thu hồi khoang", "Phần nợ vượt tiền cọc khi thu hồi khoang.", fmId);
        } else if (remaining.signum() > 0) {
            // Cọc còn dư sau khi trừ hết nợ → hàng đợi hoàn tiền như trả kho.
            refunds.requireDeposit(c.orderId(), remaining, InvoiceRefundReasons.CHECKOUT_DEPOSIT, fmId);
        }
        contracts.end(contractId, fmId);
        if (storageUnits.startManualMaintenanceAfterReturn(c.unitId())) {
            auditRecorder.record(new AuditEntry(AuditAction.STORAGE_UNIT_MAINTENANCE, AuditEntityType.STORAGE_UNIT,
                    String.valueOf(c.unitId()), Map.of("status", "Rented"),
                    Map.of("status", "Maintenance", "reason", "Thu hồi khoang do quá hạn — thay khoá"), null, fmId));
        }
        LocalDate holdUntil = today.plusDays(pricingQuery.intPolicy(POLICY_HOLD_DAYS));
        ReclaimedGoods goods = goodsRepository.saveAndFlush(ReclaimedGoods.reclaim(c.id(), c.facilityId(),
                unitCode(c), fmId, input.witnessStaffId(), note, photos, location, holdUntil, Instant.now()));
        mediaAttachments.attach(fmId, photos);
        Map<String, Object> after = new HashMap<>();
        after.put("status", goods.getStatus());
        after.put("contractId", c.id());
        after.put("witnessStaffId", input.witnessStaffId());
        after.put("holdUntil", holdUntil.toString());
        after.put("totalCharges", total);
        after.put("debt", debt);
        audit(AuditAction.CONTRACT_RECLAIMED, goods, null, after, fmId);
        notifications.notifyCustomer(c.orderId(), NotificationType.GOODS_RECLAIMED, Map.of(
                "contractCode", c.code(), "unitCode", unitCode(c), "holdUntil", DATE.format(holdUntil),
                "debt", money(debt), "holdFeePerDay", money(pricingQuery.decimalPolicy(POLICY_HOLD_FEE))));
        return view(goods);
    }

    // ------------------------------------------------------------------ nhận lại đồ

    /** Khách yêu cầu nhận lại đồ: lập hoá đơn phí lưu giữ tới hôm nay (nếu chưa có); FM được báo để chuẩn bị. */
    @Transactional
    public GoodsView requestReturn(Long goodsId, Long customerId) {
        ReclaimedGoods goods = goodsRepository.findByIdForUpdate(goodsId)
                .orElseThrow(() -> TenancyException.notFound("Không tìm thấy đồ lưu giữ"));
        ContractSnapshot c = contracts.find(goods.getContractId()).orElseThrow();
        if (!c.customerId().equals(customerId)) {
            throw TenancyException.notFound("Không tìm thấy đồ lưu giữ");
        }
        if (!goods.holding()) {
            throw TenancyException.conflict("Đồ không còn được lưu giữ");
        }
        orders.lock(c.orderId());
        if (invoices.unpaidTotal(c.id(), ContractInvoiceService.HOLD_KIND).signum() == 0) {
            BigDecimal fee = holdFeeDue(goods, c.id(), LocalDate.now(ZONE));
            if (fee.signum() > 0) {
                invoices.issue(c.orderId(), c.id(), c.customerId(), "Service", ContractInvoiceService.HOLD_KIND,
                        "Phí lưu giữ đồ sau thu hồi khoang", "Phí lưu giữ " + daysHeld(goods, LocalDate.now(ZONE))
                                + " ngày tới ngày yêu cầu nhận lại.", fee,
                        Instant.now().plus(Duration.ofDays(DEBT_DUE_DAYS)), customerId);
            }
        }
        boolean first = goods.getReturnRequestedAt() == null;
        goods.requestReturn(Instant.now());
        audit(AuditAction.GOODS_RETURN_REQUESTED, goods, null, Map.of("status", goods.getStatus()), customerId);
        if (first) {
            notifications.notifyFacilityManager(c.orderId(), NotificationType.GOODS_RETURN_REQUESTED,
                    Map.of("contractCode", c.code(), "unitCode", goods.getUnitCode()));
        }
        return view(goods);
    }

    /** FM trả đồ cho khách — khách phải trả hết công nợ và phí lưu giữ. */
    @Transactional
    public GoodsView confirmReturn(Long goodsId) {
        ReclaimedGoods goods = lockForFm(goodsId);
        ContractSnapshot c = contracts.find(goods.getContractId()).orElseThrow();
        if (goods.getReturnRequestedAt() == null) {
            throw TenancyException.conflict("Khách chưa gửi yêu cầu nhận lại đồ");
        }
        BigDecimal debt = invoices.unpaidTotal(c.id(), ContractInvoiceService.MOVE_OUT_DEBT_KIND,
                ContractInvoiceService.HOLD_KIND);
        if (debt.signum() > 0) {
            throw TenancyException.conflict("Khách còn nợ " + money(debt) + " đ — thanh toán hết rồi mới trả đồ");
        }
        Long fmId = currentAccount.getCurrentAccountId();
        goods.markReturned(fmId, Instant.now());
        audit(AuditAction.GOODS_RETURNED, goods, Map.of("status", ReclaimedGoods.HOLDING),
                Map.of("status", goods.getStatus()), fmId);
        notifications.notifyCustomer(c.orderId(), NotificationType.GOODS_RETURNED,
                Map.of("contractCode", c.code(), "unitCode", goods.getUnitCode()));
        return view(goods);
    }

    // ------------------------------------------------------------------ thanh lý

    /**
     * FM thanh lý đồ sau hạn lưu giữ: phí lưu giữ tới hôm nay cộng vào nợ; tiền thanh lý trừ nợ ({@code INV-LIQ}
     * {@code Paid}); thiếu → {@code INV-DBT} mới; dư → giữ hộ khách, FM hoàn thủ công rồi ghi nhận.
     */
    @Transactional
    public GoodsView liquidate(Long goodsId, LiquidationInput input) {
        String note = required(input == null ? null : input.note(), "Vui lòng nhập biên bản thanh lý", NOTE_MAX);
        List<String> photos = photos(input.photos());
        BigDecimal proceeds = input.proceeds();
        if (proceeds == null || proceeds.signum() < 0 || proceeds.stripTrailingZeros().scale() > 0) {
            throw TenancyException.invalid("Tiền thanh lý phải là số nguyên VND ≥ 0");
        }
        ReclaimedGoods goods = lockForFm(goodsId);
        LocalDate today = LocalDate.now(ZONE);
        if (!today.isAfter(goods.getHoldUntil())) {
            throw TenancyException.conflict("Chưa hết hạn lưu giữ (tới ngày " + DATE.format(goods.getHoldUntil())
                    + ")");
        }
        ContractSnapshot c = contracts.find(goods.getContractId()).orElseThrow();
        orders.lock(c.orderId());
        Long fmId = currentAccount.getCurrentAccountId();
        BigDecimal holdFee = holdFeeDue(goods, c.id(), today);
        BigDecimal folded = invoices.foldUnpaidOfKinds(c.id(), "Gộp vào quyết toán thanh lý đồ lưu giữ",
                ContractInvoiceService.MOVE_OUT_DEBT_KIND, ContractInvoiceService.HOLD_KIND);
        BigDecimal owed = folded.add(holdFee).setScale(2, RoundingMode.HALF_UP);
        BigDecimal covered = proceeds.min(owed);
        if (covered.signum() > 0) {
            invoices.settleByLiquidation(c.orderId(), c.id(), c.customerId(), covered,
                    "Trừ nợ bằng tiền thanh lý đồ lưu giữ", fmId);
        }
        BigDecimal shortfall = owed.subtract(covered);
        if (shortfall.signum() > 0) {
            issueDebt(c, shortfall, "Công nợ sau thanh lý đồ lưu giữ",
                    "Tiền thanh lý không đủ trả công nợ và phí lưu giữ.", fmId);
        }
        BigDecimal surplus = proceeds.subtract(covered).setScale(2, RoundingMode.HALF_UP);
        goods.liquidate(fmId, proceeds, note, photos, surplus, Instant.now());
        mediaAttachments.attach(fmId, photos);
        Map<String, Object> after = new HashMap<>();
        after.put("status", goods.getStatus());
        after.put("proceeds", proceeds);
        after.put("owed", owed);
        after.put("covered", covered);
        after.put("surplus", surplus);
        after.put("shortfall", shortfall);
        audit(AuditAction.GOODS_LIQUIDATED, goods, Map.of("status", ReclaimedGoods.HOLDING), after, fmId);
        String outcome = surplus.signum() > 0
                ? "Còn dư " + money(surplus) + "đ — cơ sở sẽ liên hệ hoàn lại cho bạn."
                : shortfall.signum() > 0
                        ? "Còn thiếu " + money(shortfall) + "đ — vui lòng thanh toán công nợ trong ứng dụng."
                        : "Công nợ đã được trả hết.";
        notifications.notifyCustomer(c.orderId(), NotificationType.GOODS_LIQUIDATED, Map.of(
                "contractCode", c.code(), "unitCode", goods.getUnitCode(), "proceeds", money(proceeds),
                "covered", money(covered), "outcome", outcome));
        if (surplus.signum() > 0) {
            notifications.notifyFacilityManager(c.orderId(), NotificationType.GOODS_SURPLUS_REFUND_REQUIRED,
                    Map.of("contractCode", c.code(), "unitCode", goods.getUnitCode(), "surplus", money(surplus)));
        }
        return view(goods);
    }

    /** FM ghi nhận đã hoàn tiền dư thanh lý cho khách (chuyển khoản / tiền mặt ngoài hệ thống). */
    @Transactional
    public GoodsView markSurplusRefunded(Long goodsId) {
        ReclaimedGoods goods = goodsRepository.findByIdForUpdate(goodsId)
                .orElseThrow(() -> TenancyException.notFound("Không tìm thấy đồ lưu giữ"));
        access.can(PERM, goods.getFacilityId());
        if (goods.getSurplusAmount() == null || goods.getSurplusAmount().signum() <= 0
                || goods.getSurplusRefundedAt() != null) {
            throw TenancyException.conflict("Không có tiền dư chờ hoàn");
        }
        Long fmId = currentAccount.getCurrentAccountId();
        goods.markSurplusRefunded(fmId, Instant.now());
        audit(AuditAction.GOODS_SURPLUS_REFUNDED, goods, null, Map.of("surplus", goods.getSurplusAmount()), fmId);
        return view(goods);
    }

    // ------------------------------------------------------------------ cron

    /** Lô đồ đang lưu giữ còn ≤ {@code storage.liquidation_notice_days} ngày tới hạn mà chưa báo khách. */
    @Transactional(readOnly = true)
    public List<Long> liquidationNoticeIds() {
        LocalDate limit = LocalDate.now(ZONE).plusDays(pricingQuery.intPolicy(POLICY_LIQUIDATION_NOTICE));
        return goodsRepository.findByStatusOrderByIdAsc(ReclaimedGoods.HOLDING).stream()
                .filter(g -> g.getLiquidationNoticeAt() == null && !g.getHoldUntil().isAfter(limit))
                .map(ReclaimedGoods::getId).toList();
    }

    /** Báo khách (+ FM) sắp thanh lý — một lần mỗi lô. */
    @Transactional
    public boolean notifyLiquidation(Long goodsId) {
        ReclaimedGoods goods = goodsRepository.findByIdForUpdate(goodsId).orElse(null);
        if (goods == null || !goods.holding() || goods.getLiquidationNoticeAt() != null) {
            return false;
        }
        ContractSnapshot c = contracts.find(goods.getContractId()).orElseThrow();
        Map<String, String> values = Map.of("contractCode", c.code(), "unitCode", goods.getUnitCode(),
                "holdUntil", DATE.format(goods.getHoldUntil()));
        notifications.notifyCustomer(c.orderId(), NotificationType.LIQUIDATION_NOTICE, values);
        notifications.notifyFacilityManager(c.orderId(), NotificationType.LIQUIDATION_NOTICE, values);
        goods.markLiquidationNoticeSent(Instant.now());
        return true;
    }

    // ------------------------------------------------------------------ internals

    private record Charge(String kind, String title, BigDecimal amount) {
    }

    private ReclaimedGoods lockForFm(Long goodsId) {
        ReclaimedGoods goods = goodsRepository.findByIdForUpdate(goodsId)
                .orElseThrow(() -> TenancyException.notFound("Không tìm thấy đồ lưu giữ"));
        access.can(PERM, goods.getFacilityId());
        if (!goods.holding()) {
            throw TenancyException.conflict("Đồ không còn được lưu giữ (" + goods.getStatus() + ")");
        }
        return goods;
    }

    private void issueDebt(ContractSnapshot c, BigDecimal amount, String title, String description, Long actorId) {
        invoices.issue(c.orderId(), c.id(), c.customerId(), "Penalty", ContractInvoiceService.MOVE_OUT_DEBT_KIND,
                title, description, amount, Instant.now().plus(Duration.ofDays(DEBT_DUE_DAYS)), actorId);
    }

    /** Phí lưu giữ từ ngày thu hồi tới {@code until} (tối thiểu 1 ngày) trừ phần phí lưu giữ đã trả. */
    private BigDecimal holdFeeDue(ReclaimedGoods goods, Long contractId, LocalDate until) {
        BigDecimal full = pricingQuery.decimalPolicy(POLICY_HOLD_FEE).multiply(BigDecimal.valueOf(
                daysHeld(goods, until)));
        return full.subtract(invoices.paidTotal(contractId, ContractInvoiceService.HOLD_KIND)).max(BigDecimal.ZERO);
    }

    private static long daysHeld(ReclaimedGoods goods, LocalDate until) {
        LocalDate from = goods.getReclaimedAt().atZone(ZONE).toLocalDate();
        return Math.max(1, ChronoUnit.DAYS.between(from, until));
    }

    /** Công nợ hiện tại: hoá đơn Unpaid bất kỳ + tiền thuê Expired của hợp đồng. */
    private BigDecimal outstanding(Long contractId) {
        return invoices.bills(contractId).stream()
                .filter(b -> b.unpaid() || ("Rental".equals(b.type()) && "Expired".equals(b.status())))
                .map(ContractBill::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void requireActiveStaff(Long staffId, Long facilityId) {
        Boolean ok = jdbc.queryForObject("""
                select exists (select 1 from accounts a
                join roles r on r.id = a.role_id and r.name = 'FS'
                join account_facility_assignments f on f.account_id = a.id and f.facility_id = ?
                where a.id = ? and a.status = 'ACTIVE')""", Boolean.class, facilityId, staffId);
        if (!Boolean.TRUE.equals(ok)) {
            throw TenancyException.invalid("FS chứng kiến không thuộc cơ sở hoặc không hoạt động");
        }
    }

    private GoodsView view(ReclaimedGoods g) {
        ContractSnapshot c = contracts.find(g.getContractId()).orElseThrow();
        LocalDate today = LocalDate.now(ZONE);
        BigDecimal debt = invoices.unpaidTotal(c.id(), ContractInvoiceService.MOVE_OUT_DEBT_KIND,
                ContractInvoiceService.HOLD_KIND);
        String witness = jdbc.query("select coalesce(name, email) from accounts where id = ?",
                (rs, i) -> rs.getString(1), g.getWitnessStaffId()).stream().findFirst().orElse(null);
        return new GoodsView(g.getId(), c.id(), c.code(), c.orderId(), g.getUnitCode(), g.getStatus(),
                g.getReclaimedAt(), witness, g.getInventoryNote(), g.getPhotos(), g.getStorageLocation(),
                g.getHoldUntil(), daysHeld(g, today), pricingQuery.decimalPolicy(POLICY_HOLD_FEE), debt,
                g.getReturnRequestedAt(), g.holding() && g.getReturnRequestedAt() != null && debt.signum() == 0,
                g.holding() && today.isAfter(g.getHoldUntil()), g.getReturnedAt(), g.getLiquidatedAt(),
                g.getProceedsAmount(), g.getLiquidationNote(), g.getLiquidationPhotos(), g.getSurplusAmount(),
                g.getSurplusRefundedAt());
    }

    private String unitCode(ContractSnapshot c) {
        return contracts.unitCode(c.id()).orElse("");
    }

    private static String required(String value, String message, int max) {
        if (value == null || value.isBlank()) {
            throw TenancyException.invalid(message);
        }
        String trimmed = value.trim();
        if (trimmed.length() > max) {
            throw TenancyException.invalid("Nội dung tối đa " + max + " ký tự");
        }
        return trimmed;
    }

    private static List<String> photos(List<String> photos) {
        List<String> list = photos == null ? List.of()
                : photos.stream().filter(p -> p != null && !p.isBlank()).map(String::trim).toList();
        if (list.isEmpty()) {
            throw TenancyException.invalid("Cần ít nhất 1 ảnh");
        }
        if (list.size() > PHOTOS_MAX) {
            throw TenancyException.invalid("Tối đa " + PHOTOS_MAX + " ảnh");
        }
        return list;
    }

    private static String money(BigDecimal amount) {
        return String.format(VI, "%,d", amount.setScale(0, RoundingMode.HALF_UP).longValue());
    }

    private void audit(AuditAction action, ReclaimedGoods goods, Object before, Object after, Long actorId) {
        auditRecorder.record(new AuditEntry(action, AuditEntityType.RECLAIMED_GOODS, String.valueOf(goods.getId()),
                before, after, null, actorId));
    }
}
