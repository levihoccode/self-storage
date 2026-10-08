import { Bell } from "lucide-react";
import { type CSSProperties, useEffect, useRef, useState } from "react";
import { authGateway } from "../../app/auth";
import {
  fetchNotificationSummaries,
  formatNotificationTime,
  markNotificationRead,
  notificationPath,
  useUnreadNotificationCount,
  type NotificationSummary,
} from "../../app/notifications";
import { NotificationDetailDialog } from "./NotificationDetailDialog";

const DROPDOWN_ITEM_LIMIT = 6;

/**
 * Chuông thông báo (spec `fe-pages/shared/03`): dropdown 5-10 item gần nhất +
 * link tới trang đầy đủ, dữ liệu từ API `/api/notifications`.
 *
 * Panel neo vào mép phải nội dung header (không dính theo vị trí chuông) để
 * không tràn viewport ở width hẹp; animation chảy ra từ đúng vị trí chuông.
 * Bấm item → mark read + mở dialog chi tiết (body lấy khi mở).
 */
export function NotificationBell({ onNavigate }: { onNavigate: (path: string) => void }) {
  const [open, setOpen] = useState(false);
  const [anchor, setAnchor] = useState<{ x: number; inset: number } | null>(null);
  const [items, setItems] = useState<NotificationSummary[] | null>(null);
  const [detail, setDetail] = useState<NotificationSummary | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [loadError, setLoadError] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);
  const unreadCount = useUnreadNotificationCount();
  const session = authGateway.getSession();
  const role = session?.user.role ?? "CUSTOMER";

  useEffect(() => {
    function handlePointerDown(event: PointerEvent) {
      if (containerRef.current && !containerRef.current.contains(event.target as Node)) {
        setOpen(false);
      }
    }
    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === "Escape") setOpen(false);
    }
    document.addEventListener("pointerdown", handlePointerDown);
    document.addEventListener("keydown", handleKeyDown);
    return () => {
      document.removeEventListener("pointerdown", handlePointerDown);
      document.removeEventListener("keydown", handleKeyDown);
    };
  }, []);

  async function loadItems() {
    setIsLoading(true);
    setLoadError(false);
    try {
      setItems(await fetchNotificationSummaries(DROPDOWN_ITEM_LIMIT));
    } catch {
      setLoadError(true);
    } finally {
      setIsLoading(false);
    }
  }

  function togglePanel() {
    if (open) {
      setOpen(false);
      return;
    }
    // Đo chuông + header để neo panel vào mép phải nội dung header (bằng đúng
    // padding của shell — FM px-6, customer pill px-4) và lấy gốc animation
    // đúng vị trí chuông.
    const container = containerRef.current;
    const header = container?.closest("header");
    if (container && header) {
      const bellRect = container.getBoundingClientRect();
      const headerRect = header.getBoundingClientRect();
      const inset = Number.parseFloat(window.getComputedStyle(header).paddingRight) || 24;
      setAnchor({
        inset,
        x: Math.round(headerRect.right - inset - (bellRect.left + bellRect.width / 2)),
      });
    }
    setOpen(true);
    void loadItems();
  }

  async function openNotification(notification: NotificationSummary) {
    setOpen(false);
    try {
      await markNotificationRead(notification.id);
    } catch {
      // Không chặn mở chi tiết; badge sẽ được làm mới ở lần mở sau.
    }
    setItems(
      (current) =>
        current?.map((item) =>
          item.id === notification.id ? { ...item, readAt: new Date().toISOString() } : item,
        ) ?? current,
    );
    setDetail(notification);
  }

  const latest = (items ?? []).slice(0, DROPDOWN_ITEM_LIMIT);

  return (
    <div ref={containerRef}>
      <button
        className="relative grid h-[38px] w-[38px] place-items-center rounded-sm border border-border bg-surface text-ink"
        onClick={togglePanel}
        aria-label={unreadCount > 0 ? `Thông báo (${unreadCount} chưa đọc)` : "Thông báo"}
        aria-expanded={open}
        aria-haspopup="menu"
      >
        <Bell size={18} />
        {unreadCount > 0 && (
          <span className="absolute -right-1.5 -top-1.5 grid h-[18px] min-w-[18px] place-items-center rounded-full bg-brand px-1 text-[10px] font-bold text-dark">
            {unreadCount}
          </span>
        )}
      </button>
      {open && (
        <div
          className="absolute top-[calc(100%+10px)] z-40 w-[min(360px,calc(100vw-32px))] animate-[notification-panel-in_240ms_cubic-bezier(0.22,1,0.36,1)] rounded-[6px] border border-border bg-surface p-2 shadow-[var(--shadow-soft),0_0_18px_rgba(53,133,142,0.18)]"
          role="menu"
          style={
            {
              right: `${anchor?.inset ?? 24}px`,
              "--notif-origin-x": anchor ? `${anchor.x}px` : undefined,
            } as CSSProperties
          }
        >
          <div className="flex items-center justify-between border-b border-border p-2.5">
            <strong className="text-[12px]">Thông báo</strong>
            {unreadCount > 0 && (
              <span className="text-[11px] text-muted">{unreadCount} chưa đọc</span>
            )}
          </div>
          {isLoading && !items ? (
            <p className="m-0 p-3 text-[12px] text-muted">Đang tải thông báo…</p>
          ) : loadError && !items ? (
            <p className="m-0 p-3 text-[12px] text-danger">Không tải được thông báo.</p>
          ) : latest.length === 0 ? (
            <p className="m-0 p-3 text-[12px] text-muted">Chưa có thông báo nào.</p>
          ) : (
            latest.map((notification) => {
              const isRead = !!notification.readAt;
              return (
                <button
                  key={notification.id}
                  className="flex w-full items-start gap-2.5 border-0 border-b border-border bg-transparent px-2.5 py-3 text-left last:border-b-0 hover:bg-brand-soft"
                  role="menuitem"
                  onClick={() => void openNotification(notification)}
                >
                  <span
                    className={`mt-[6px] h-2 w-2 shrink-0 rounded-full ${isRead ? "bg-border" : "bg-brand"}`}
                  />
                  <span className="min-w-0">
                    <span className="block text-[12px] font-bold text-ink">
                      {notification.title}
                    </span>
                    <span className="mt-1 block font-mono text-[10px] uppercase tracking-[0.06em] text-muted">
                      {formatNotificationTime(notification.createdAt)}
                    </span>
                  </span>
                </button>
              );
            })
          )}
          <button
            className="mt-1 flex w-full items-center justify-center gap-[9px] border-0 bg-transparent px-2.5 py-[11px] text-[12px] font-bold text-brand hover:bg-brand-soft"
            role="menuitem"
            onClick={() => {
              setOpen(false);
              onNavigate("/notifications");
            }}
          >
            Xem tất cả
          </button>
        </div>
      )}
      {detail && (
        <NotificationDetailDialog
          notification={detail}
          path={notificationPath(detail, role)}
          onClose={() => setDetail(null)}
          onNavigate={(path) => {
            setDetail(null);
            onNavigate(path);
          }}
        />
      )}
    </div>
  );
}
