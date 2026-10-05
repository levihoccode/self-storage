import type { UserRole } from "../app/auth";

export type NotificationGroup = "approval" | "invoice" | "assignment" | "expiry" | "other";

export type Notification = {
  id: string;
  group: NotificationGroup;
  title: string;
  content: string;
  sentAt: string;
  isRead: boolean;
  /** Deep link tới resource liên quan; bỏ trống khi thông báo chỉ để đọc (type OTHER). */
  path?: string;
  /** Role nhận — mock thay cho `Notification.account_id` khi chưa có API. */
  audience: UserRole;
  /** Chỉ set khi gửi cho một account cụ thể (type OTHER); bỏ trống = mọi account cùng role. */
  recipientEmail?: string;
};

export const NOTIFICATION_GROUP_LABEL: Record<NotificationGroup, string> = {
  approval: "Duyệt / Từ chối",
  invoice: "Hoá đơn",
  assignment: "Phân công",
  expiry: "Cảnh báo hết hạn",
  other: "Khác",
};

export type NotificationRecipient = { name: string; email: string };

/** Danh bạ customer cho form "Tạo thông báo" (mock; thay bằng API khi BE sẵn sàng). */
export const notificationRecipients: NotificationRecipient[] = [
  { name: "Nguyễn Minh Anh", email: "customer1@lemar.vn" },
  { name: "Trần Thu Hà", email: "customer2@lemar.vn" },
  { name: "Lê Quốc Bảo", email: "customer3@lemar.vn" },
];

const CUSTOM_NOTIFICATIONS_STORAGE_KEY = "kho-moc-custom-notifications";

/** Thông báo type OTHER đã tạo trong phiên demo — persist để sống qua reload/đổi account. */
function readCustomNotifications(): Notification[] {
  try {
    const raw = window.localStorage.getItem(CUSTOM_NOTIFICATIONS_STORAGE_KEY);
    return raw ? (JSON.parse(raw) as Notification[]) : [];
  } catch {
    return [];
  }
}

function writeCustomNotifications() {
  try {
    window.localStorage.setItem(
      CUSTOM_NOTIFICATIONS_STORAGE_KEY,
      JSON.stringify(notifications.filter((item) => item.id.startsWith("notif-custom-"))),
    );
  } catch {
    // Demo vẫn chạy trong tab hiện tại khi storage không khả dụng.
  }
}

export const notifications: Notification[] = [
  ...readCustomNotifications(),
  {
    id: "notif-001",
    group: "approval",
    title: "Đề xuất khoang mới cho yêu cầu của bạn",
    content: "Đội ngũ vận hành vừa đề xuất khoang D-045 tại Kho Mộc — Tân Bình Hub.",
    sentAt: "27/09/2026 09:12",
    isRead: false,
    path: "/proposals",
    audience: "CUSTOMER",
  },
  {
    id: "notif-002",
    group: "invoice",
    title: "Hoá đơn đặt cọc cần thanh toán",
    content: "Hoá đơn HD-000501 (980.000đ) sắp đến hạn 09/10/2026.",
    sentAt: "26/09/2026 16:40",
    isRead: false,
    path: "/invoices",
    audience: "CUSTOMER",
  },
  {
    id: "notif-003",
    group: "approval",
    title: "Yêu cầu gia hạn đã được duyệt",
    content: "Gia hạn 3 tháng cho khoang B-014 đã được duyệt, chờ bạn thanh toán.",
    sentAt: "25/09/2026 11:05",
    isRead: false,
    path: "/my-storage/storage-002",
    audience: "CUSTOMER",
  },
  {
    id: "notif-004",
    group: "expiry",
    title: "Hợp đồng sắp hết hạn",
    content:
      "Hợp đồng khoang B-014 hết hạn vào 28/12/2025. Gửi yêu cầu gia hạn sớm để không gián đoạn.",
    sentAt: "24/09/2026 08:00",
    isRead: true,
    path: "/my-storage/storage-002",
    audience: "CUSTOMER",
  },
  {
    id: "notif-005",
    group: "assignment",
    title: "Đã phân công nhân viên check-in",
    content: "Nhân viên Nguyễn Thành Được sẽ hỗ trợ bàn giao khoang cho bạn.",
    sentAt: "22/09/2026 14:30",
    isRead: true,
    path: "/appointments/new?orderId=order-501",
    audience: "CUSTOMER",
  },
  {
    id: "notif-006",
    group: "assignment",
    title: "Yêu cầu hỗ trợ đã được xử lý",
    content: "Sự cố mã truy cập cổng phụ tại khoang A-208 đã được xử lý xong.",
    sentAt: "02/08/2025 10:00",
    isRead: true,
    path: "/my-storage/storage-001",
    audience: "CUSTOMER",
  },
  // FM — theo catalog trong specs/db-table-draft.md (PROPOSAL_*, APPOINTMENT_*, HANDOVER_*, RENTAL_ORDER_CANCELED).
  {
    id: "notif-fm-001",
    group: "approval",
    title: "Cần đề xuất khoang khác",
    content: "Đơn order-501: khoang B-014 không còn khả dụng. Chọn khoang mới để gửi khách duyệt.",
    sentAt: "28/09/2026 08:15",
    isRead: false,
    path: "/fm/rental-orders/order-501/re-propose",
    audience: "FM",
  },
  {
    id: "notif-fm-002",
    group: "approval",
    title: "Khách hàng đã từ chối đề xuất",
    content: "Khách từ chối khoang D-045 (đơn order-502), lý do: chưa phù hợp vị trí.",
    sentAt: "27/09/2026 17:40",
    isRead: false,
    path: "/fm/rental-requests",
    audience: "FM",
  },
  {
    id: "notif-fm-003",
    group: "assignment",
    title: "Có lịch check-in mới cần phân công",
    content:
      "Đơn order-503 hẹn 09:00 ngày 30/09/2026 tại Kho Mộc — Tân Bình Hub, chưa có FS phụ trách.",
    sentAt: "27/09/2026 14:05",
    isRead: false,
    path: "/fm/appointments",
    audience: "FM",
  },
  {
    id: "notif-fm-004",
    group: "assignment",
    title: "Lịch check-in chưa có nhân viên phụ trách",
    content: "Đơn order-504 còn 1 ngày tới hạn hẹn mà chưa gán FS.",
    sentAt: "26/09/2026 09:30",
    isRead: true,
    path: "/fm/appointments",
    audience: "FM",
  },
  {
    id: "notif-fm-005",
    group: "approval",
    title: "Khách từ chối khoang tại check-in",
    content: "Khoang A-208 (đơn order-505) bị từ chối tại check-in; cần đề xuất khoang khác.",
    sentAt: "25/09/2026 16:20",
    isRead: true,
    path: "/fm/rental-orders/order-505/re-propose",
    audience: "FM",
  },
  {
    id: "notif-fm-006",
    group: "expiry",
    title: "Đơn thuê kho đã bị hủy",
    content: "Đơn order-506 quá hạn thanh toán cọc và đã bị hủy; khoang được giải phóng.",
    sentAt: "24/09/2026 10:00",
    isRead: true,
    path: "/fm/rental-requests",
    audience: "FM",
  },
  // FS — catalog có FS_ASSIGNED; FS chưa có shell/route nên chưa gắn deep link.
  {
    id: "notif-fs-001",
    group: "assignment",
    title: "Bạn được phân công lịch check-in",
    content: "Lịch check-in đơn order-503 lúc 09:00 ngày 30/09/2026 tại Kho Mộc — Tân Bình Hub.",
    sentAt: "28/09/2026 15:10",
    isRead: false,
    audience: "FS",
  },
  {
    id: "notif-fs-002",
    group: "assignment",
    title: "Lịch bàn giao ngày mai",
    content: "Nhắc lịch bàn giao khoang B-014 (đơn order-507) lúc 14:00 ngày 01/10/2026.",
    sentAt: "27/09/2026 18:00",
    isRead: true,
    audience: "FS",
  },
];

/**
 * DEMO ONLY — in-memory "read" tracking so the notifications page and the
 * sidebar badge agree on read state across navigation without a backend.
 * Local component state alone resets every time NotificationsPage unmounts
 * (i.e. every time the user navigates away and back).
 */
const readNotificationIds = new Set(
  notifications
    .filter((notification) => notification.isRead)
    .map((notification) => notification.id),
);

export function isNotificationRead(id: string) {
  return readNotificationIds.has(id);
}

export function markNotificationRead(id: string) {
  readNotificationIds.add(id);
}

/** Chỉ đánh dấu các item đang hiển thị — tránh role này đọc hết thông báo của role khác. */
export function markAllNotificationsRead(items: Notification[]) {
  items.forEach((notification) => readNotificationIds.add(notification.id));
}

/** Thông báo của role hiện tại; item gắn `recipientEmail` chỉ hiện với đúng account đó. */
export function notificationsFor(role: UserRole, email?: string) {
  return notifications.filter(
    (notification) =>
      notification.audience === role &&
      (!notification.recipientEmail || notification.recipientEmail === email),
  );
}

export function unreadNotificationCount(role: UserRole, email?: string) {
  return notificationsFor(role, email).filter(
    (notification) => !readNotificationIds.has(notification.id),
  ).length;
}

let customNotificationSeq = 0;

/**
 * DEMO ONLY — thông báo thủ công type `OTHER` (spec: recipient do người tạo
 * chọn; quyền tạo thật chờ Flow 5.0). Thay bằng POST /api/notifications khi có API.
 */
export function addNotification(input: {
  recipient: NotificationRecipient;
  title: string;
  content: string;
}): Notification {
  const now = new Date();
  const pad = (value: number) => String(value).padStart(2, "0");
  const item: Notification = {
    id: `notif-custom-${Date.now()}-${++customNotificationSeq}`,
    group: "other",
    title: input.title,
    content: input.content,
    sentAt: `${pad(now.getDate())}/${pad(now.getMonth() + 1)}/${now.getFullYear()} ${pad(now.getHours())}:${pad(now.getMinutes())}`,
    isRead: false,
    audience: "CUSTOMER",
    recipientEmail: input.recipient.email,
  };
  notifications.unshift(item);
  writeCustomNotifications();
  return item;
}
