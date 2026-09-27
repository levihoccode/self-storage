import { useMemo, useState } from "react";
import {
  Bell,
  CheckCheck,
  ChevronRight,
  CircleAlert,
  Clock3,
  FileText,
  ShieldAlert,
  Sparkles,
} from "lucide-react";
import type { Navigate } from "../../app/types";
import "./NotificationsCenter.css";

type NotificationGroup = "all" | "approval" | "invoice" | "schedule" | "warning";

type NotificationItem = {
  id: string;
  type: string;
  title: string;
  content: string;
  isRead: boolean;
  time: string;
  group: Exclude<NotificationGroup, "all">;
  destination: string;
};

const initialNotifications: NotificationItem[] = [
  {
    id: "nt-101",
    type: "RentalRequest.Approved",
    title: "Yêu cầu lưu trữ đã được duyệt",
    content: "Yêu cầu kho Q7 của bạn đã được FM phê duyệt. Bạn có thể xem chi tiết và tiếp tục quy trình thanh toán.",
    isRead: false,
    time: "5 phút trước",
    group: "approval",
    destination: "/my-storage",
  },
  {
    id: "nt-102",
    type: "Invoice.DueSoon",
    title: "Hóa đơn cần thanh toán",
    content: "Hóa đơn cọc số INV-2048 sắp đến hạn thanh toán trong 48 giờ. Vui lòng kiểm tra để tránh giãn tiến độ.",
    isRead: false,
    time: "2 giờ trước",
    group: "invoice",
    destination: "/my-storage",
  },
  {
    id: "nt-103",
    type: "Schedule.Assigned",
    title: "Bạn được phân công hỗ trợ kiểm tra kho",
    content: "FS đã được giao lịch kiểm tra kho tại Q9 vào ngày mai 08:30. Vui lòng xác nhận tham gia.",
    isRead: true,
    time: "Hôm qua",
    group: "schedule",
    destination: "/bom/staff-requests",
  },
  {
    id: "nt-104",
    type: "Contract.Expiring",
    title: "Hợp đồng sắp hết hạn",
    content: "Hợp đồng lưu trữ của bạn sẽ hết hiệu lực trong 7 ngày. Hãy xem lại thời gian gia hạn phù hợp.",
    isRead: true,
    time: "2 ngày trước",
    group: "warning",
    destination: "/my-storage",
  },
];

const groupMeta: Record<Exclude<NotificationGroup, "all">, { label: string; icon: typeof Bell }> = {
  approval: { label: "Phê duyệt", icon: Sparkles },
  invoice: { label: "Hóa đơn", icon: FileText },
  schedule: { label: "Lịch làm việc", icon: Clock3 },
  warning: { label: "Cảnh báo", icon: ShieldAlert },
};

export function NotificationsCenter({ navigate }: { navigate: Navigate }) {
  const [notifications, setNotifications] = useState<NotificationItem[]>(initialNotifications);
  const [filter, setFilter] = useState<NotificationGroup>("all");

  const filteredNotifications = useMemo(() => {
    return filter === "all"
      ? notifications
      : notifications.filter((item) => item.group === filter);
  }, [filter, notifications]);

  const unreadCount = notifications.filter((item) => !item.isRead).length;

  function markAllRead() {
    setNotifications((current) => current.map((item) => ({ ...item, isRead: true })));
  }

  function markAsRead(id: string) {
    setNotifications((current) =>
      current.map((item) => (item.id === id ? { ...item, isRead: true } : item)),
    );
  }

  function openNotification(item: NotificationItem) {
    markAsRead(item.id);
    navigate(item.destination);
  }

  return (
    <main className="notifications-page">
      <section className="notifications-hero">
        <div className="container notifications-hero__inner">
          <div>
            <p className="eyebrow">Shared</p>
            <h1>
              Notifications <span>center.</span>
            </h1>
            <p>
              Theo dõi thông báo về yêu cầu, hóa đơn, lịch làm việc và các cảnh báo quan trọng của hệ thống.
            </p>
          </div>
          <div className="notifications-badge-wrap">
            <div className="notifications-badge">
              <Bell size={18} />
              <span>{unreadCount} chưa đọc</span>
            </div>
          </div>
        </div>
      </section>

      <section className="container notifications-content">
        <div className="notifications-toolbar">
          <div className="notifications-filters" aria-label="Bộ lọc thông báo">
            {(["all", "approval", "invoice", "schedule", "warning"] as NotificationGroup[]).map((option) => (
              <button
                key={option}
                type="button"
                className={`filter-chip ${filter === option ? "is-active" : ""}`}
                onClick={() => setFilter(option)}
              >
                {option === "all" ? "Tất cả" : groupMeta[option].label}
              </button>
            ))}
          </div>

          <button type="button" className="button button-secondary" onClick={markAllRead}>
            <CheckCheck size={16} /> Đánh dấu đã đọc tất cả
          </button>
        </div>

        <div className="notifications-list">
          {filteredNotifications.length === 0 ? (
            <div className="empty-state">
              <CircleAlert size={24} />
              <p>Chưa có thông báo nào phù hợp với bộ lọc hiện tại.</p>
            </div>
          ) : (
            filteredNotifications.map((item) => {
              const MetaIcon = groupMeta[item.group].icon;

              return (
                <button
                  key={item.id}
                  type="button"
                  className={`notification-item ${item.isRead ? "is-read" : "is-unread"}`}
                  onClick={() => openNotification(item)}
                >
                  <div className={`notification-icon notification-icon--${item.group}`}>
                    <MetaIcon size={18} />
                  </div>

                  <div className="notification-body">
                    <div className="notification-header">
                      <span className="notification-type">{groupMeta[item.group].label}</span>
                      {!item.isRead && <span className="notification-dot" aria-label="Chưa đọc" />}
                    </div>

                    <h3>{item.title}</h3>
                    <p>{item.content}</p>
                    <div className="notification-meta">
                      <span>{item.time}</span>
                      <span>{item.type}</span>
                    </div>
                  </div>

                  <ChevronRight size={18} className="notification-arrow" />
                </button>
              );
            })
          )}
        </div>
      </section>
    </main>
  );
}
