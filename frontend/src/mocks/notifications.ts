export type NotificationGroup = "approval" | "invoice" | "assignment" | "expiry";

export type Notification = {
  id: string;
  group: NotificationGroup;
  title: string;
  content: string;
  sentAt: string;
  isRead: boolean;
  path: string;
};

export const NOTIFICATION_GROUP_LABEL: Record<NotificationGroup, string> = {
  approval: "Duyệt / Từ chối",
  invoice: "Hoá đơn",
  assignment: "Phân công",
  expiry: "Cảnh báo hết hạn",
};

export const notifications: Notification[] = [
  {
    id: "notif-001",
    group: "approval",
    title: "Đề xuất khoang mới cho yêu cầu của bạn",
    content: "Đội ngũ vận hành vừa đề xuất khoang D-045 tại Kho Mộc — Tân Bình Hub.",
    sentAt: "27/09/2026 09:12",
    isRead: false,
    path: "/proposals",
  },
  {
    id: "notif-002",
    group: "invoice",
    title: "Hoá đơn đặt cọc cần thanh toán",
    content: "Hoá đơn HD-000501 (980.000đ) sắp đến hạn 09/10/2026.",
    sentAt: "26/09/2026 16:40",
    isRead: false,
    path: "/invoices",
  },
  {
    id: "notif-003",
    group: "approval",
    title: "Yêu cầu gia hạn đã được duyệt",
    content: "Gia hạn 3 tháng cho khoang B-014 đã được duyệt, chờ bạn thanh toán.",
    sentAt: "25/09/2026 11:05",
    isRead: false,
    path: "/my-storage/storage-002",
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
  },
  {
    id: "notif-005",
    group: "assignment",
    title: "Đã phân công nhân viên check-in",
    content: "Nhân viên Nguyễn Thành Được sẽ hỗ trợ bàn giao khoang cho bạn.",
    sentAt: "22/09/2026 14:30",
    isRead: true,
    path: "/appointments/new?orderId=order-501",
  },
  {
    id: "notif-006",
    group: "assignment",
    title: "Yêu cầu hỗ trợ đã được xử lý",
    content: "Sự cố mã truy cập cổng phụ tại khoang A-208 đã được xử lý xong.",
    sentAt: "02/08/2025 10:00",
    isRead: true,
    path: "/my-storage/storage-001",
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

export function markAllNotificationsRead() {
  notifications.forEach((notification) => readNotificationIds.add(notification.id));
}

export function unreadNotificationCount() {
  return notifications.filter((notification) => !readNotificationIds.has(notification.id)).length;
}
