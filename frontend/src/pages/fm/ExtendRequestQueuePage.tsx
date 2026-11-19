import { AlertTriangle, Check, Lock, User } from "lucide-react";
import { useState } from "react";
import { formatDate, formatDateTime, formatMoney } from "../../app/format";
import { fetchFacilityStaff, type FmStaff } from "../../app/fmStaff";
import {
  assignOverlock,
  fetchFacilityOverlocks,
  fetchOverdueContracts,
  INVOICE_STATUS_LABEL,
  labelOf,
  loadErrorState,
  useFmAction,
  useFmLoad,
  type FmOverlock,
} from "../../app/services/fm";
import { Button } from "../../components/ui/Button";
import { DemoNotice } from "../../components/ui/DemoNotice";
import { INPUT_ELEMENT, INPUT_WRAP } from "../../components/ui/FormField";
import { StatusTag } from "../../components/ui/StatusTag";
import { SurfaceState } from "../../components/ui/SurfaceState";

/** Khoá chặn của cơ sở trên khoang dùng chìa cơ (FS gắn / gỡ ở trang Khoá chặn). */
const OVERLOCK_LABEL: Record<string, string> = {
  Required: "chờ FS gắn khoá chặn",
  Applied: "đã gắn khoá chặn",
  RemovalRequired: "chờ FS gỡ khoá chặn",
};

const OVERLOCK_TAG: Record<
  string,
  { label: string; tone: "success" | "warning" | "danger" | "info" | "neutral" }
> = {
  Required: { label: "Chờ gắn", tone: "warning" },
  Applied: { label: "Đang gắn", tone: "danger" },
  RemovalRequired: { label: "Chờ gỡ", tone: "info" },
};

function staffLabel(staff: FmStaff) {
  return staff.name ? `${staff.name} · ${staff.email}` : staff.email;
}

const TABLE_COLUMNS = "grid-cols-[1.2fr_0.8fr_0.7fr_0.7fr_0.9fr_1.3fr_0.9fr]";

/**
 * Route `/fm/extend-requests`: gia hạn đã tự động (khách tự tạo hoá đơn gia hạn), FM không
 * duyệt nữa — màn này hiển thị hợp đồng quá hạn của cơ sở và phí quá hạn đang cộng dồn.
 */
export function ExtendRequestQueuePage() {
  const overdue = useFmLoad("overdue-contracts", (facility) => fetchOverdueContracts(facility.id));
  const list = overdue.data ?? [];

  return (
    <div className="grid gap-6">
      <div>
        <h1 className="m-0 text-title text-ink">Hợp đồng quá hạn</h1>
        <p className="mt-1.5 text-small text-muted">
          Hợp đồng đang hiệu lực đã qua ngày kết thúc tại{" "}
          {overdue.facility ? `cơ sở ${overdue.facility.name}` : "cơ sở của bạn"}. Khách tự gia hạn
          trên ứng dụng; phí quá hạn do hệ thống cộng dồn mỗi giờ.
        </p>
      </div>

      <OverlockSection />

      {overdue.error ? (
        <SurfaceState
          {...loadErrorState(overdue.error, "hợp đồng quá hạn")}
          action={{ label: "Thử lại", onClick: overdue.reload }}
        />
      ) : !overdue.data ? (
        <SurfaceState variant="loading" title="Đang tải hợp đồng quá hạn…" />
      ) : list.length === 0 ? (
        <SurfaceState
          variant="empty"
          title="Không có hợp đồng quá hạn"
          description="Mọi hợp đồng đang hiệu lực của cơ sở đều còn trong hạn."
        />
      ) : (
        <div className="overflow-x-auto">
          <div className="min-w-[960px]">
            <div
              className={`grid ${TABLE_COLUMNS} gap-4 bg-[var(--dark)] px-3.5 py-2.5 text-label text-[rgba(233,234,230,.62)]`}
            >
              <span>Hợp đồng</span>
              <span>Hết hạn</span>
              <span>Quá hạn</span>
              <span>Ngày tính phí</span>
              <span>Phí cộng dồn</span>
              <span>Hoá đơn quá hạn đang mở</span>
              <span>Trả kho</span>
            </div>
            <div className="mt-1.5 grid gap-1.5">
              {list.map((contract) => (
                <div
                  key={contract.contractId}
                  className={`grid ${TABLE_COLUMNS} items-center gap-4 bg-surface px-3.5 py-3`}
                >
                  <div className="min-w-0">
                    <strong className="block truncate text-small font-semibold text-ink">
                      {contract.contractCode}
                    </strong>
                    <span className="mt-0.5 block text-label text-muted">
                      Khoang {contract.unitCode ?? "—"} · Đơn #{contract.orderId}
                    </span>
                    {(contract.accessSuspended || contract.overlockStatus) && (
                      <span className="mt-0.5 block text-label font-semibold text-danger">
                        {contract.accessSuspended ? "Tạm khoá truy cập" : "Đã mở lại truy cập"}
                        {contract.overlockStatus
                          ? ` · ${OVERLOCK_LABEL[contract.overlockStatus] ?? contract.overlockStatus}`
                          : ""}
                      </span>
                    )}
                  </div>
                  <span className="text-label text-ink tabular-nums">
                    {formatDate(contract.endDate)}
                  </span>
                  <span className="flex items-center gap-1.5 text-small font-semibold text-danger tabular-nums">
                    <AlertTriangle size={13} /> {contract.daysOverdue} ngày
                  </span>
                  <span className="text-small text-ink tabular-nums">
                    {contract.chargedDays} ngày
                  </span>
                  <span className="text-small font-semibold text-ink tabular-nums">
                    {formatMoney(contract.accrued)}
                  </span>
                  <div className="min-w-0 text-label">
                    {contract.openInvoice ? (
                      <>
                        <span className="block truncate text-ink">
                          {contract.openInvoice.code} · {formatMoney(contract.openInvoice.amount)}
                        </span>
                        <span className="mt-0.5 block text-muted">
                          {labelOf(INVOICE_STATUS_LABEL, contract.openInvoice.status)} · hạn{" "}
                          {formatDateTime(contract.openInvoice.dueDate)}
                        </span>
                      </>
                    ) : (
                      <span className="text-muted">Chưa có</span>
                    )}
                  </div>
                  <span>
                    {contract.returnRequested ? (
                      <StatusTag tone="info">Đã gửi yêu cầu</StatusTag>
                    ) : (
                      <StatusTag tone="warning">Chưa yêu cầu</StatusTag>
                    )}
                  </span>
                </div>
              ))}
            </div>
            <p className="mt-3 text-label text-muted">{list.length} hợp đồng quá hạn</p>
          </div>
        </div>
      )}
    </div>
  );
}

/**
 * Việc khoá chặn đang mở của cơ sở: hệ thống tự tạo khi tạm khoá (gắn) / mở lại (gỡ); FM giao một FS cụ thể — đã
 * giao thì chỉ FS đó xác nhận ở trang Khoá chặn.
 */
function OverlockSection() {
  const overlocks = useFmLoad("overlocks", (facility) => fetchFacilityOverlocks(facility.id));
  const staff = useFmLoad("staff", (facility) => fetchFacilityStaff(facility.id));
  const [notice, setNotice] = useState<string | null>(null);
  const list = overlocks.data ?? [];

  return (
    <section className="grid gap-2">
      <div className="flex flex-wrap items-end justify-between gap-2">
        <div>
          <h2 className="m-0 flex items-center gap-2 text-section text-ink">
            <Lock size={16} /> Khoá chặn của cơ sở
          </h2>
          <p className="mt-1 text-small text-muted">
            Khoang dùng chìa cơ bị tạm khoá do quá hạn cần gắn khoá chặn; khi mở lại thì cần gỡ.
            Giao một FS đi làm — FS đó nhận thông báo và xác nhận ở trang Khoá chặn.
          </p>
        </div>
      </div>
      {notice && <DemoNotice tone="success">{notice}</DemoNotice>}
      {staff.error && (
        <DemoNotice tone="error">Không tải được danh sách FS: {staff.error.message}</DemoNotice>
      )}
      {overlocks.error ? (
        <SurfaceState
          {...loadErrorState(overlocks.error, "khoá chặn")}
          action={{ label: "Thử lại", onClick: overlocks.reload }}
        />
      ) : !overlocks.data ? (
        <SurfaceState variant="loading" title="Đang tải khoá chặn…" />
      ) : list.length === 0 ? (
        <p className="m-0 bg-surface p-4 text-small text-muted">
          Không có khoá chặn nào cần gắn / gỡ.
        </p>
      ) : (
        <div className={`grid gap-1.5 ${overlocks.loading ? "opacity-60" : ""}`}>
          {list.map((item) => (
            <OverlockRow
              key={item.id}
              item={item}
              staff={staff.data ?? []}
              onAssigned={(result, member) => {
                setNotice(
                  `Đã giao ${member ? staffLabel(member) : "FS"} ${
                    result.status === "Required" ? "gắn" : "gỡ"
                  } khoá chặn khoang ${result.unitCode}.`,
                );
                overlocks.reload();
              }}
            />
          ))}
        </div>
      )}
    </section>
  );
}

function OverlockRow({
  item,
  staff,
  onAssigned,
}: {
  item: FmOverlock;
  staff: FmStaff[];
  onAssigned: (result: FmOverlock, member: FmStaff | undefined) => void;
}) {
  const [staffId, setStaffId] = useState("");
  const action = useFmAction();
  const tag = OVERLOCK_TAG[item.status] ?? { label: item.status, tone: "neutral" as const };
  const assignable = item.status === "Required" || item.status === "RemovalRequired";
  const selectId = `overlock-staff-${item.id}`;

  async function assign() {
    const outcome = await action.run("assign", () => assignOverlock(item.id, Number(staffId)));
    if (outcome.ok) {
      onAssigned(
        outcome.value,
        staff.find((member) => member.id === Number(staffId)),
      );
    }
  }

  return (
    <div className="grid grid-cols-[minmax(0,1fr)_minmax(0,1fr)_minmax(0,1.4fr)] items-start gap-4 bg-surface p-4 max-[900px]:grid-cols-1">
      <div className="min-w-0">
        <strong className="block text-small font-semibold text-ink">Khoang {item.unitCode}</strong>
        <span className="mt-0.5 block text-label text-muted">Hợp đồng {item.contractCode}</span>
        <span className="mt-1.5 inline-block">
          <StatusTag tone={tag.tone}>{tag.label}</StatusTag>
        </span>
      </div>
      <div className="min-w-0 text-label text-muted">
        <span className="block">Yêu cầu gắn {formatDateTime(item.requiredAt)}</span>
        {item.appliedAt && <span className="block">Đã gắn {formatDateTime(item.appliedAt)}</span>}
        {item.removalRequestedAt && (
          <span className="block">Mở lại {formatDateTime(item.removalRequestedAt)}</span>
        )}
        {item.note && <span className="block">Ghi chú: {item.note}</span>}
      </div>
      <div>
        <span className="mb-2 flex items-center gap-2 text-small text-ink">
          <User size={14} />
          {item.assignedStaffId !== null
            ? `Phụ trách: ${item.assignedStaffName ?? `FS #${item.assignedStaffId}`}`
            : "Chưa giao — mọi FS của cơ sở đều thấy việc"}
        </span>
        {!assignable ? (
          <span className="text-label text-muted">
            Đang gắn — khi hợp đồng được mở lại, việc gỡ sẽ hiện ở đây để giao.
          </span>
        ) : staff.length === 0 ? (
          <span className="text-label text-muted">Cơ sở chưa có FS để giao.</span>
        ) : (
          <div className="flex flex-wrap items-center gap-2">
            <div className={`${INPUT_WRAP} min-w-[200px] flex-1`}>
              <label htmlFor={selectId} className="sr-only">
                Chọn FS
              </label>
              <select
                id={selectId}
                className={INPUT_ELEMENT}
                value={staffId}
                onChange={(event) => {
                  setStaffId(event.target.value);
                  action.clear();
                }}
              >
                <option value="">— Chọn FS —</option>
                {staff.map((member) => (
                  <option key={member.id} value={member.id}>
                    {staffLabel(member)}
                  </option>
                ))}
              </select>
            </div>
            <Button
              size="sm"
              variant={item.assignedStaffId === null ? "primary" : "secondary"}
              pending={action.pending === "assign"}
              disabled={!staffId}
              onClick={assign}
            >
              {action.pending !== "assign" && <Check size={14} />}
              {item.assignedStaffId === null
                ? item.status === "Required"
                  ? "Giao FS gắn"
                  : "Giao FS gỡ"
                : "Giao lại"}
            </Button>
          </div>
        )}
        {action.error && <DemoNotice tone="error">{action.error}</DemoNotice>}
      </div>
    </div>
  );
}
