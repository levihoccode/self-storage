import { useEffect, useSyncExternalStore } from "react";
import { apiRequest } from "./api";
import { authGateway, type UserRole } from "./auth";

export type NotificationGroup = "approval" | "invoice" | "assignment" | "expiry" | "other";

export type NotificationItem = {
  id: number;
  type: string;
  title: string;
  body: string;
  readAt: string | null;
  createdAt: string;
  orderId: number | null;
};

/** Summary từ BE cố ý không có `body`; FE lấy detail để đủ dữ liệu hiển thị. */
type NotificationSummary = Omit<NotificationItem, "body">;

const GROUP_BY_TYPE: Record<string, NotificationGroup> = {
  PROPOSAL_REJECTED: "approval",
  PROPOSAL_REPROPOSAL_REQUIRED: "approval",
  PROPOSAL_REPROPOSED: "approval",
  RENTAL_REQUEST_APPROVED: "approval",
  RENTAL_REQUEST_REJECTED: "approval",
  HANDOVER_REJECTED: "approval",
  DEPOSIT_PAYMENT_SUCCEEDED: "invoice",
  APPOINTMENT_CREATED: "assignment",
  APPOINTMENT_CANCELED_NO_SHOW: "assignment",
  FS_ASSIGNED: "assignment",
  FS_ASSIGNMENT_REQUIRED: "assignment",
  HANDOVER_OVERDUE: "assignment",
  HANDOVER_COMPLETED: "assignment",
  RENTAL_ORDER_EXPIRING_SOON: "expiry",
  RENTAL_ORDER_CANCELED: "expiry",
};

export function notificationGroup(type: string): NotificationGroup {
  return GROUP_BY_TYPE[type] ?? "other";
}

/**
 * Deep link theo `type` + `orderId` cho từng role. `OTHER` và các type chưa có
 * màn hình (vd FS) không gắn link — bấm chỉ để đánh dấu đã đọc.
 */
export function notificationPath(
  item: { type: string; orderId: number | null },
  role: UserRole,
): string | undefined {
  const orderId = item.orderId;
  switch (item.type) {
    case "PROPOSAL_REPROPOSAL_REQUIRED":
    case "HANDOVER_REJECTED":
      if (role === "FM") {
        return orderId ? `/fm/rental-orders/${orderId}/re-propose` : "/fm/rental-requests";
      }
      return undefined;
    case "PROPOSAL_REJECTED":
    case "RENTAL_ORDER_CANCELED":
      if (role === "FM") return "/fm/rental-requests";
      return role === "CUSTOMER" ? "/my-storage" : undefined;
    case "APPOINTMENT_CREATED":
    case "FS_ASSIGNMENT_REQUIRED":
      if (role === "FM") return "/fm/appointments";
      return role === "CUSTOMER" ? "/appointments/new" : undefined;
    case "RENTAL_REQUEST_APPROVED":
    case "RENTAL_REQUEST_REJECTED":
    case "PROPOSAL_REPROPOSED":
      return role === "CUSTOMER" ? "/proposals" : undefined;
    case "DEPOSIT_PAYMENT_SUCCEEDED":
      return role === "CUSTOMER" ? "/invoices" : undefined;
    case "APPOINTMENT_CANCELED_NO_SHOW":
    case "HANDOVER_OVERDUE":
      return role === "CUSTOMER" ? "/appointments/new" : undefined;
    case "RENTAL_ORDER_EXPIRING_SOON":
    case "HANDOVER_COMPLETED":
      return role === "CUSTOMER" ? "/my-storage" : undefined;
    default:
      return undefined;
  }
}

export function formatNotificationTime(iso: string): string {
  const date = new Date(iso);
  const pad = (value: number) => String(value).padStart(2, "0");
  return `${pad(date.getDate())}/${pad(date.getMonth() + 1)}/${date.getFullYear()} ${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

function accessToken(): string | undefined {
  return authGateway.getSession()?.token;
}

/**
 * Lấy summary rồi bù `body` bằng detail từng item. BE cố ý bỏ `body` khỏi
 * summary; FE spec (`fe-pages/shared/03`) yêu cầu item hiển thị nội dung —
 * đây là N+1, đề xuất BE thêm `body` vào summary hoặc endpoint bulk.
 */
export async function fetchNotifications(): Promise<NotificationItem[]> {
  const summaries = await apiRequest<NotificationSummary[]>("/api/notifications?page=0&size=100", {
    accessToken: accessToken(),
  });
  return Promise.all(
    summaries.map(async (summary) => {
      try {
        return await apiRequest<NotificationItem>(`/api/notifications/${summary.id}`, {
          accessToken: accessToken(),
        });
      } catch {
        return { ...summary, body: "" };
      }
    }),
  );
}

export async function markNotificationRead(id: number): Promise<void> {
  await apiRequest(`/api/notifications/${id}/read`, { method: "PATCH", accessToken: accessToken() });
  await refreshUnreadCount();
}

export async function markAllNotificationsRead(): Promise<void> {
  await apiRequest("/api/notifications/read-all", { method: "PATCH", accessToken: accessToken() });
  await refreshUnreadCount();
}

/** Store nhỏ cho badge chuông + sidebar, dùng chung giữa các shell. */
let unreadCount = 0;
const listeners = new Set<() => void>();

function emit() {
  listeners.forEach((listener) => listener());
}

export function getUnreadCount() {
  return unreadCount;
}

export function subscribeUnread(listener: () => void) {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}

export async function refreshUnreadCount(): Promise<void> {
  try {
    const { unreadCount: next } = await apiRequest<{ unreadCount: number }>(
      "/api/notifications/unread-count",
      { accessToken: accessToken() },
    );
    unreadCount = next;
    emit();
  } catch {
    // Lỗi mạng thì giữ số cũ; lần refresh sau sẽ cập nhật.
  }
}

export function useUnreadNotificationCount(): number {
  const count = useSyncExternalStore(subscribeUnread, getUnreadCount, () => 0);
  useEffect(() => {
    void refreshUnreadCount();
  }, []);
  return count;
}
