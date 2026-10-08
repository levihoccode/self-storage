import { AlertTriangle, CheckCheck, Megaphone, Receipt, UserCheck } from "lucide-react";
import { useEffect, useState } from "react";
import { authGateway } from "../app/auth";
import {
  fetchNotificationPage,
  formatNotificationTime,
  markAllNotificationsRead,
  markNotificationRead,
  notificationGroup,
  notificationPath,
  useUnreadNotificationCount,
  type NotificationGroup,
  type NotificationItem,
} from "../app/notifications";
import type { Navigate } from "../app/types";
import { Button } from "../components/ui/Button";
import { SurfaceState } from "../components/ui/SurfaceState";

const GROUP_ICON: Record<NotificationGroup, typeof AlertTriangle> = {
  approval: CheckCheck,
  invoice: Receipt,
  assignment: UserCheck,
  expiry: AlertTriangle,
  other: Megaphone,
};

const PAGE_SIZE = 20;

const GROUP_ICON_CLASS: Record<NotificationGroup, string> = {
  approval: "bg-success/14 text-success",
  invoice: "bg-brand-soft text-brand",
  assignment: "bg-brand-soft text-accent",
  expiry: "bg-danger/14 text-danger",
  other: "bg-muted/14 text-muted",
};

export function NotificationsPage({ navigate }: { navigate: Navigate }) {
  const [items, setItems] = useState<NotificationItem[] | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [loadError, setLoadError] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);
  const [page, setPage] = useState(0);
  const [hasMore, setHasMore] = useState(false);
  const [isLoadingMore, setIsLoadingMore] = useState(false);
  const [readFilter, setReadFilter] = useState<"all" | "unread">("all");
  const unreadCount = useUnreadNotificationCount();
  const session = authGateway.getSession();
  const role = session?.user.role ?? "CUSTOMER";

  useEffect(() => {
    let isActive = true;
    async function loadInitial() {
      try {
        const data = await fetchNotificationPage(0, PAGE_SIZE);
        if (isActive) {
          setItems(data);
          setHasMore(data.length === PAGE_SIZE);
        }
      } catch {
        if (isActive) setLoadError(true);
      } finally {
        if (isActive) setIsLoading(false);
      }
    }
    void loadInitial();
    return () => {
      isActive = false;
    };
  }, []);

  // Retry từ nút trong error state — setState nằm ở event handler, không phải effect.
  async function retry() {
    setIsLoading(true);
    setLoadError(false);
    try {
      const data = await fetchNotificationPage(
        0,
        PAGE_SIZE,
        readFilter === "unread" ? false : undefined,
      );
      setItems(data);
      setPage(0);
      setHasMore(data.length === PAGE_SIZE);
    } catch {
      setLoadError(true);
    } finally {
      setIsLoading(false);
    }
  }

  async function changeFilter(next: "all" | "unread") {
    if (next === readFilter) return;
    setReadFilter(next);
    setPage(0);
    setIsLoading(true);
    setLoadError(false);
    setActionError(null);
    try {
      const data = await fetchNotificationPage(0, PAGE_SIZE, next === "unread" ? false : undefined);
      setItems(data);
      setHasMore(data.length === PAGE_SIZE);
    } catch {
      setLoadError(true);
    } finally {
      setIsLoading(false);
    }
  }

  async function loadMore() {
    setIsLoadingMore(true);
    setActionError(null);
    try {
      const nextPage = page + 1;
      const data = await fetchNotificationPage(
        nextPage,
        PAGE_SIZE,
        readFilter === "unread" ? false : undefined,
      );
      setItems((current) => [...(current ?? []), ...data]);
      setPage(nextPage);
      setHasMore(data.length === PAGE_SIZE);
    } catch {
      setActionError("Không tải thêm được — thử lại sau.");
    } finally {
      setIsLoadingMore(false);
    }
  }

  const list = items ?? [];
  const unreadOnly = readFilter === "unread";

  async function openNotification(notification: NotificationItem) {
    setActionError(null);
    try {
      await markNotificationRead(notification.id);
      setItems((current) => {
        if (!current) return current;
        // Đang lọc "Chưa đọc" thì item đã đọc phải rời danh sách.
        if (unreadOnly) return current.filter((item) => item.id !== notification.id);
        return current.map((item) =>
          item.id === notification.id ? { ...item, readAt: new Date().toISOString() } : item,
        );
      });
    } catch {
      setActionError("Không đánh dấu đã đọc được — thử lại sau.");
    }
    const path = notificationPath(notification, role);
    if (path) navigate(path);
  }

  async function markAllRead() {
    setActionError(null);
    try {
      await markAllNotificationsRead();
      setItems((current) => {
        if (!current) return current;
        if (unreadOnly) return [];
        return current.map((item) =>
          item.readAt ? item : { ...item, readAt: new Date().toISOString() },
        );
      });
    } catch {
      setActionError("Không đánh dấu tất cả được — thử lại sau.");
    }
  }

  return (
    <main>
      <div className="mb-8 flex items-start justify-between gap-5 max-[760px]:flex-col">
        <div>
          <h1 className="m-0 text-[28px] tracking-[-0.03em] text-ink">Trung tâm thông báo</h1>
          <p className="mt-2 text-[13px] text-muted">
            {isLoading
              ? "Đang tải thông báo…"
              : unreadCount > 0
                ? `${unreadCount} thông báo chưa đọc`
                : "Bạn đã đọc hết thông báo"}
          </p>
        </div>
        <div className="flex flex-wrap items-center gap-2.5">
          <div
            className="inline-flex overflow-hidden rounded-sm border border-border"
            role="group"
            aria-label="Lọc thông báo"
          >
            {(
              [
                ["all", "Tất cả"],
                ["unread", "Chưa đọc"],
              ] as const
            ).map(([value, label]) => (
              <button
                key={value}
                className={`border-0 px-[14px] py-[9px] text-[12px] font-bold transition-colors ${
                  readFilter === value
                    ? "bg-brand-soft text-ink"
                    : "bg-transparent text-muted hover:text-ink"
                }`}
                aria-pressed={readFilter === value}
                onClick={() => void changeFilter(value)}
              >
                {label}
              </button>
            ))}
          </div>
          <Button
            variant="secondary"
            disabled={isLoading || unreadCount === 0}
            onClick={markAllRead}
          >
            <CheckCheck size={16} /> Đánh dấu đã đọc tất cả
          </Button>
        </div>
      </div>

      {actionError && (
        <p className="mb-4 mt-0 rounded-sm border border-danger/40 bg-danger/14 px-3 py-[11px] text-[12px] text-danger">
          {actionError}
        </p>
      )}

      {isLoading ? (
        <SurfaceState variant="loading" title="Đang tải thông báo…" />
      ) : loadError ? (
        <SurfaceState
          variant="error"
          title="Không tải được thông báo"
          description="Kiểm tra kết nối tới máy chủ rồi thử lại."
          action={{ label: "Thử lại", onClick: () => void retry() }}
        />
      ) : list.length === 0 ? (
        <SurfaceState
          variant="empty"
          title={unreadOnly ? "Không có thông báo chưa đọc" : "Chưa có thông báo nào"}
        />
      ) : (
        <>
          <div className="grid gap-2.5">
            {list.map((notification) => {
              const group = notificationGroup(notification.type);
              const Icon = GROUP_ICON[group];
              const isRead = !!notification.readAt;
              return (
                <button
                  key={notification.id}
                  className={`flex items-start gap-4 border p-5 text-left transition-colors hover:border-brand max-[760px]:flex-col max-[760px]:items-stretch ${
                    isRead ? "border-border bg-surface" : "border-l-4 border-brand bg-brand-soft"
                  }`}
                  onClick={() => void openNotification(notification)}
                >
                  <span
                    className={`grid h-9 w-9 shrink-0 place-items-center rounded-full ${GROUP_ICON_CLASS[group]}`}
                  >
                    <Icon size={17} />
                  </span>
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center justify-between gap-3">
                      <p
                        className={`m-0 text-[14px] ${isRead ? "font-semibold text-ink" : "font-bold text-ink"}`}
                      >
                        {notification.title}
                      </p>
                      {!isRead && <span className="h-2 w-2 shrink-0 rounded-full bg-brand" />}
                    </div>
                    <p className="mb-0 mt-1.5 text-[13px] leading-[1.5] text-muted">
                      {notification.body}
                    </p>
                    <p className="mb-0 mt-2 font-mono text-[10px] uppercase tracking-[0.06em] text-muted">
                      {formatNotificationTime(notification.createdAt)}
                    </p>
                  </div>
                </button>
              );
            })}
          </div>
          {hasMore && (
            <div className="mt-4 flex justify-center">
              <Button variant="secondary" pending={isLoadingMore} onClick={() => void loadMore()}>
                Xem thêm
              </Button>
            </div>
          )}
        </>
      )}
    </main>
  );
}
