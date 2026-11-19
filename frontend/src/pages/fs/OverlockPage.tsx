import { Lock, LockOpen, MapPin } from "lucide-react";
import { useEffect, useState } from "react";
import { formatDateTime } from "../../app/format";
import { errorMessage, errorStatus } from "../../app/http";
import {
  applyOverlock,
  fetchStaffOverlocks,
  OVERLOCK_STATUS,
  removeOverlock,
  type OverlockView,
} from "../../app/services/staff";
import type { Navigate } from "../../app/types";
import { Button } from "../../components/ui/Button";
import { DemoNotice } from "../../components/ui/DemoNotice";
import { INPUT_ELEMENT, INPUT_WRAP } from "../../components/ui/FormField";
import { StatusTag } from "../../components/ui/StatusTag";
import { SurfaceState } from "../../components/ui/SurfaceState";

/**
 * FS — khoá chặn của cơ sở trên khoang dùng chìa cơ khi hợp đồng quá hạn kéo dài bị tạm khoá: gắn khi được yêu
 * cầu, gỡ khi truy cập đã mở lại (khách trả phí + gia hạn, hoặc đến buổi trả kho).
 */
export function OverlockPage({ navigate }: { navigate: Navigate }) {
  const [items, setItems] = useState<OverlockView[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<{ status: number; message: string } | null>(null);
  const [reloadKey, setReloadKey] = useState(0);
  const [busyId, setBusyId] = useState<number | null>(null);
  const [notes, setNotes] = useState<Record<number, string>>({});
  const [actionError, setActionError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    (async () => {
      try {
        const list = await fetchStaffOverlocks();
        if (active) setItems(list);
      } catch (caught) {
        if (active) setError({ status: errorStatus(caught), message: errorMessage(caught) });
      } finally {
        if (active) setIsLoading(false);
      }
    })();
    return () => {
      active = false;
    };
  }, [reloadKey]);

  function reload() {
    setIsLoading(true);
    setError(null);
    setReloadKey((key) => key + 1);
  }

  async function act(item: OverlockView, kind: "apply" | "remove") {
    setBusyId(item.id);
    setActionError(null);
    setNotice(null);
    try {
      const note = notes[item.id];
      if (kind === "apply") {
        await applyOverlock(item.id, note);
        setNotice(`Đã ghi nhận gắn khoá chặn khoang ${item.unitCode}.`);
      } else {
        await removeOverlock(item.id, note);
        setNotice(`Đã ghi nhận gỡ khoá chặn khoang ${item.unitCode} — khách đã được báo.`);
      }
      setNotes((current) => ({ ...current, [item.id]: "" }));
      reload();
    } catch (caught) {
      setActionError(errorMessage(caught));
      reload();
    } finally {
      setBusyId(null);
    }
  }

  return (
    <div className="grid gap-6">
      <div>
        <h1 className="m-0 text-title text-ink">Khoá chặn</h1>
        <p className="mt-1.5 text-small text-muted">
          Khoang dùng chìa cơ của hợp đồng quá hạn kéo dài bị tạm khoá: gắn khoá chặn của cơ sở để
          khách không mở được bằng chìa đang giữ. Khi truy cập được mở lại (khách trả phí và gia
          hạn, hoặc đến buổi trả kho), gỡ khoá chặn tại khoang rồi xác nhận ở đây.
        </p>
      </div>

      {actionError && <DemoNotice tone="error">{actionError}</DemoNotice>}
      {notice && !actionError && <DemoNotice tone="success">{notice}</DemoNotice>}

      {isLoading ? (
        <SurfaceState variant="loading" title="Đang tải việc khoá chặn…" />
      ) : error ? (
        error.status === 403 ? (
          <SurfaceState
            variant="forbidden"
            title="Bạn không có quyền xử lý khoá chặn"
            description={error.message}
          />
        ) : (
          <SurfaceState
            variant="error"
            title="Không tải được việc khoá chặn"
            description={error.message}
            action={{ label: "Thử lại", onClick: reload }}
          />
        )
      ) : items.length === 0 ? (
        <SurfaceState
          variant="empty"
          title="Không có khoá chặn nào cần xử lý"
          description="Khi một hợp đồng dùng chìa cơ bị tạm khoá do quá hạn, việc gắn khoá chặn sẽ hiện ở đây."
          action={{ label: "Mở lịch làm việc", onClick: () => navigate("/staff/schedule") }}
        />
      ) : (
        <div className="grid gap-2">
          {items.map((item) => {
            const status = OVERLOCK_STATUS[item.status] ?? {
              label: item.status,
              tone: "neutral" as const,
            };
            const actionable = item.status === "Required" || item.status === "RemovalRequired";
            return (
              <article key={item.id} className="grid gap-3 bg-surface p-4">
                <div className="flex flex-wrap items-start justify-between gap-3">
                  <div className="min-w-0">
                    <span className="text-label text-muted">
                      Hợp đồng {item.contractCode} · {item.facilityName}
                    </span>
                    <strong className="mt-1 flex items-center gap-1.5 text-section text-ink">
                      <MapPin size={16} /> Khoang {item.unitCode}
                    </strong>
                    <span className="mt-1 block text-small text-muted">
                      Yêu cầu gắn {formatDateTime(item.requiredAt)}
                      {item.appliedAt ? ` · đã gắn ${formatDateTime(item.appliedAt)}` : ""}
                      {item.removalRequestedAt
                        ? ` · mở lại ${formatDateTime(item.removalRequestedAt)}`
                        : ""}
                    </span>
                    {item.note && (
                      <span className="mt-1 block text-small text-muted">Ghi chú: {item.note}</span>
                    )}
                  </div>
                  <StatusTag tone={status.tone}>{status.label}</StatusTag>
                </div>
                {actionable ? (
                  <div className="flex flex-wrap items-end gap-2">
                    <label className={`${INPUT_WRAP} min-w-[240px] flex-1`}>
                      <input
                        className={INPUT_ELEMENT}
                        maxLength={500}
                        placeholder="Ghi chú (tuỳ chọn) — vd. số khoá chặn"
                        value={notes[item.id] ?? ""}
                        onChange={(event) =>
                          setNotes((current) => ({ ...current, [item.id]: event.target.value }))
                        }
                      />
                    </label>
                    {item.status === "Required" ? (
                      <Button
                        pending={busyId === item.id}
                        disabled={busyId !== null}
                        onClick={() => act(item, "apply")}
                      >
                        <Lock size={14} /> Đã gắn khoá chặn
                      </Button>
                    ) : (
                      <Button
                        pending={busyId === item.id}
                        disabled={busyId !== null}
                        onClick={() => act(item, "remove")}
                      >
                        <LockOpen size={14} /> Đã gỡ khoá chặn
                      </Button>
                    )}
                  </div>
                ) : (
                  <p className="m-0 text-small text-muted">
                    Đang gắn — chờ khách trả phí quá hạn và gia hạn, hoặc đến buổi trả kho. Hệ thống
                    báo khi cần gỡ.
                  </p>
                )}
              </article>
            );
          })}
        </div>
      )}
    </div>
  );
}
