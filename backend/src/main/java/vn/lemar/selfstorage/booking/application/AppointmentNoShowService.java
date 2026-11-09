package vn.lemar.selfstorage.booking.application;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.lemar.selfstorage.audit.application.AuditEntry;
import vn.lemar.selfstorage.audit.application.AuditRecorder;
import vn.lemar.selfstorage.audit.domain.AuditAction;
import vn.lemar.selfstorage.audit.domain.AuditEntityType;
import vn.lemar.selfstorage.booking.domain.Appointment;
import vn.lemar.selfstorage.booking.domain.AppointmentStatus;
import vn.lemar.selfstorage.booking.repository.AppointmentReadRepository;
import vn.lemar.selfstorage.booking.repository.AppointmentReadRepository.AppointmentRow;
import vn.lemar.selfstorage.booking.repository.AppointmentRepository;
import vn.lemar.selfstorage.notification.application.NotificationType;

/**
 * Cron no-show: lịch check-in Pending quá {@code end_at} → Canceled (Quá giờ và khách không đến), audit,
 * báo khách, đóng phiên bàn giao và đưa đơn về trạng thái đúng.
 *
 * <p>Thứ tự xử lý giữ đúng thứ tự khoá {@code appointments → handover_records → rental_orders}:
 * khoá lịch, huỷ lịch + audit, phát {@link AppointmentNoShow} cho {@code handover}, rồi mới cập nhật
 * đơn. Idempotent: chỉ lịch {@code Pending} quá hạn mới chạy; lần hai trả {@code false}.
 */
@Service
public class AppointmentNoShowService {

    static final String REASON = "Quá giờ và khách không đến";

    private static final Logger LOG = LoggerFactory.getLogger(AppointmentNoShowService.class);
    private static final ZoneId FACILITY_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm").withZone(FACILITY_ZONE);

    private final AppointmentRepository appointmentRepository;
    private final AppointmentReadRepository readRepository;
    private final RentalOrderQuery orderQuery;
    private final RentalOrderLifecycle orderLifecycle;
    private final RentalOrderCancellationService orderCancellation;
    private final OrderNotifier orderNotifier;
    private final AuditRecorder auditRecorder;
    private final ApplicationEventPublisher events;
    private final String frontendUrl;

    AppointmentNoShowService(AppointmentRepository appointmentRepository,
                             AppointmentReadRepository readRepository, RentalOrderQuery orderQuery,
                             RentalOrderLifecycle orderLifecycle,
                             RentalOrderCancellationService orderCancellation,
                             OrderNotifier orderNotifier, AuditRecorder auditRecorder,
                             ApplicationEventPublisher events,
                             @Value("${app.frontend-url}") String frontendUrl) {
        this.appointmentRepository = appointmentRepository;
        this.readRepository = readRepository;
        this.orderQuery = orderQuery;
        this.orderLifecycle = orderLifecycle;
        this.orderCancellation = orderCancellation;
        this.orderNotifier = orderNotifier;
        this.auditRecorder = auditRecorder;
        this.events = events;
        this.frontendUrl = frontendUrl;
    }

    @Transactional(readOnly = true)
    public List<Long> findDueIds() {
        return appointmentRepository.findNoShowDueIds(Instant.now());
    }

    /** Huỷ một lịch nếu đang due; trả {@code true} khi thực sự huỷ. */
    @Transactional
    public boolean cancelIfDue(Long appointmentId) {
        Appointment appointment = appointmentRepository.findByIdForUpdate(appointmentId).orElse(null);
        Instant now = Instant.now();
        if (appointment == null || !appointment.isNoShowDue(now)) {
            return false;
        }
        appointment.cancel(REASON);
        Map<String, Object> newValue = new HashMap<>();
        newValue.put("status", AppointmentStatus.Canceled.name());
        newValue.put("cancelReason", REASON);
        auditRecorder.record(new AuditEntry(AuditAction.APPOINTMENT_CANCELED_NO_SHOW,
                AuditEntityType.APPOINTMENT, String.valueOf(appointmentId),
                Map.of("status", AppointmentStatus.Pending.name()), newValue, REASON, null));

        appointmentRepository.flush();
        // handover nghe event ngay trong transaction này (khoá handover_records) — chạy trước khi
        // booking khoá rental_orders để giữ thứ tự khoá chung với cron handover quá hạn.
        events.publishEvent(new AppointmentNoShow(appointmentId, REASON));

        AppointmentRow row = readRepository.findById(appointmentId).orElse(null);
        if (row != null) {
            applyOrderOutcome(row.orderId(), now);
            orderNotifier.notifyCustomer(appointment.getCustomerId(), row.orderId(),
                    NotificationType.APPOINTMENT_CANCELED_NO_SHOW,
                    Map.of("appointmentTime", appointmentTime(row),
                            "rescheduleLink", frontendUrl + "/appointments/new"));
        }
        return true;
    }

    /**
     * Đơn của lịch no-show: còn hạn giữ kho → về {@code Deposited} để khách đặt lịch mới; hết hạn →
     * Cancellation Cascade với lý do quá hạn giữ. Đơn không còn {@code InProgress} thì chỉ đóng
     * lịch/phiên bàn giao, không đoán trạng thái.
     */
    private void applyOrderOutcome(Long orderId, Instant now) {
        if (orderId == null) {
            return;
        }
        RentalOrderQuery.Snapshot order = orderQuery.find(orderId).orElse(null);
        if (order == null) {
            LOG.warn("Lịch no-show trỏ đơn không tồn tại {}", orderId);
            return;
        }
        if (!order.inProgress()) {
            LOG.warn("Đơn {} không InProgress ({}) — chỉ đóng lịch/phiên bàn giao", orderId, order.status());
            return;
        }
        if (order.expiresAt() == null || now.isBefore(order.expiresAt())) {
            orderLifecycle.returnToDeposited(orderId, REASON);
        } else {
            orderCancellation.cancel(orderId, REASON);
        }
    }

    private static String appointmentTime(AppointmentRow row) {
        return TIME.format(row.startedAt()) + "–" + TIME.format(row.endAt()) + " " + DATE.format(row.date());
    }
}
