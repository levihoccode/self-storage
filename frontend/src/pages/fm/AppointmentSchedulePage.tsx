import { Check, Loader2, Lock, User } from "lucide-react";
import { useState } from "react";
import { fmAppointments as initialAppointments, fsStaff, type FmAppointment } from "../../mocks/fm";
import { DemoNotice } from "../../components/ui/DemoNotice";
import { DetailPanel } from "../../components/ui/DetailPanel";
import { SurfaceState } from "../../components/ui/SurfaceState";

const TYPE_LABEL: Record<FmAppointment["type"], string> = {
  CHECKIN: "Check-in",
  RETURN: "Trả kho",
};

const TYPE_BADGE: Record<FmAppointment["type"], string> = {
  CHECKIN: "bg-success/14 text-success",
  RETURN: "bg-info/14 text-info",
};

const PRIMARY_BUTTON =
  "inline-flex min-h-11 items-center justify-center gap-2.5 rounded-sm border border-transparent bg-brand px-[18px] text-[14px] font-[750] text-background transition-colors duration-[180ms] ease hover:bg-brand-strong disabled:cursor-not-allowed disabled:opacity-60";
const FILTER_SELECT =
  "min-w-[170px] rounded-sm border border-border bg-surface px-[11px] py-[10px] pr-[30px] text-[12px] font-semibold text-ink";

function staffName(staffId: string | null) {
  return fsStaff.find((staff) => staff.id === staffId)?.name ?? null;
}

function groupByDate(items: FmAppointment[]) {
  const groups = new Map<string, FmAppointment[]>();
  for (const item of items) {
    const bucket = groups.get(item.date) ?? [];
    bucket.push(item);
    groups.set(item.date, bucket);
  }
  return Array.from(groups.entries());
}

export function AppointmentSchedulePage() {
  const [items, setItems] = useState<FmAppointment[]>(initialAppointments);
  const [unassignedOnly, setUnassignedOnly] = useState(false);
  const [openId, setOpenId] = useState<string | null>(null);
  const [selectedStaffId, setSelectedStaffId] = useState("");
  const [busyId, setBusyId] = useState<string | null>(null);

  const openAppointment = items.find((item) => item.id === openId) ?? null;

  const list = items.filter((item) => !unassignedOnly || item.staffId === null);
  const groups = groupByDate(list);

  function openDetails(appointment: FmAppointment) {
    setOpenId(appointment.id);
    setSelectedStaffId("");
  }

  function assign(appointment: FmAppointment) {
    if (!selectedStaffId) return;
    setBusyId(appointment.id);
    window.setTimeout(() => {
      setBusyId(null);
      setItems((current) =>
        current.map((item) =>
          item.id === appointment.id ? { ...item, staffId: selectedStaffId } : item,
        ),
      );
      setOpenId(null);
    }, 700);
  }

  return (
    <main>
      <div className="mb-8">
        <h1 className="m-0 text-[28px] tracking-[-0.03em] text-ink">Lịch hẹn &amp; phân công FS</h1>
        <p className="mt-2 text-[13px] text-muted">
          Lịch check-in và trả kho tại cơ sở của bạn, theo ngày.
        </p>
      </div>

      <div className="mb-6">
        <DemoNotice tone="pending">
          Lịch <strong>trả kho</strong> được hệ thống tự phân công từ hàng chờ yêu cầu trả kho — ở
          đây chỉ xem, không phân công lại để tránh trùng lặp.
        </DemoNotice>
      </div>

      <div className="mb-6 flex flex-wrap items-center gap-3">
        <select
          className={FILTER_SELECT}
          value={unassignedOnly ? "unassigned" : "all"}
          onChange={(event) => setUnassignedOnly(event.target.value === "unassigned")}
        >
          <option value="all">Tất cả lịch hẹn</option>
          <option value="unassigned">Chỉ chưa phân công</option>
        </select>
      </div>

      {groups.length === 0 ? (
        <SurfaceState variant="empty" title="Không có lịch hẹn nào phù hợp bộ lọc" />
      ) : (
        <div className="grid gap-8">
          {groups.map(([date, dayItems]) => (
            <section key={date}>
              <p className="m-0 mb-3 font-mono text-[11px] font-bold uppercase tracking-[0.08em] text-brand">
                {date}
              </p>
              <div className="grid gap-3">
                {dayItems.map((appointment) => {
                  const assignedName = staffName(appointment.staffId);
                  const isReadOnly = appointment.type === "RETURN";
                  return (
                    <button
                      key={appointment.id}
                      className="flex items-start justify-between gap-5 border border-border bg-surface p-5 text-left transition-colors hover:border-brand disabled:cursor-default disabled:hover:border-border max-[760px]:flex-col"
                      onClick={() => !isReadOnly && openDetails(appointment)}
                      disabled={isReadOnly}
                    >
                      <div>
                        <div className="flex items-center gap-2.5">
                          <span
                            className={`rounded-full px-2 py-0.5 text-[10px] font-bold ${TYPE_BADGE[appointment.type]}`}
                          >
                            {TYPE_LABEL[appointment.type]}
                          </span>
                          <p className="m-0 text-[15px] font-bold text-ink">
                            {appointment.customerName}
                          </p>
                        </div>
                        <p className="mb-0 mt-1.5 text-[13px] text-muted">
                          Khoang {appointment.unitCode} · {appointment.timeSlot}
                        </p>
                      </div>
                      <span className="flex items-center gap-1.5 whitespace-nowrap text-[12px] font-bold text-muted">
                        {isReadOnly && <Lock size={13} />}
                        {assignedName ? (
                          <>
                            <User size={14} /> {assignedName}
                          </>
                        ) : (
                          <span className="rounded-full bg-warning/14 px-2 py-0.5 text-[11px] font-extrabold text-warning">
                            Chưa phân công
                          </span>
                        )}
                      </span>
                    </button>
                  );
                })}
              </div>
            </section>
          ))}
        </div>
      )}

      {openAppointment && (
        <DetailPanel
          title={openAppointment.customerName}
          eyebrow={`${TYPE_LABEL[openAppointment.type]} · ${openAppointment.date}`}
          onClose={() => setOpenId(null)}
        >
          <div className="grid gap-2.5 text-[13px] text-muted">
            <span>Khoang {openAppointment.unitCode}</span>
            <span>{openAppointment.timeSlot}</span>
          </div>

          {staffName(openAppointment.staffId) ? (
            <div className="mt-6">
              <DemoNotice tone="success">
                Đã phân công {staffName(openAppointment.staffId)} phụ trách lịch hẹn này.
              </DemoNotice>
            </div>
          ) : (
            <>
              <label className="mt-6 grid gap-1.5 text-[12px] font-bold text-ink">
                Chọn nhân viên FS
                <select
                  className="rounded-sm border border-border bg-surface-subtle px-3 py-2.5 text-[13px] text-ink"
                  value={selectedStaffId}
                  onChange={(event) => setSelectedStaffId(event.target.value)}
                >
                  <option value="">— Chọn nhân viên —</option>
                  {fsStaff.map((staff) => (
                    <option key={staff.id} value={staff.id}>
                      {staff.name} · {staff.phone}
                    </option>
                  ))}
                </select>
              </label>
              <div className="mt-5">
                <button
                  className={PRIMARY_BUTTON}
                  disabled={!selectedStaffId || busyId === openAppointment.id}
                  onClick={() => assign(openAppointment)}
                >
                  {busyId === openAppointment.id ? (
                    <Loader2
                      className="animate-[surface-state-spin_0.9s_linear_infinite]"
                      size={16}
                    />
                  ) : (
                    <Check size={16} />
                  )}
                  Phân công
                </button>
              </div>
            </>
          )}
        </DetailPanel>
      )}
    </main>
  );
}
