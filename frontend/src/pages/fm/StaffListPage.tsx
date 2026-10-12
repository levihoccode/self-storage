import { CalendarDays, LifeBuoy, Mail, Phone, Undo2, UserRound } from "lucide-react";
import { useState } from "react";
import {
  fmAppointments,
  fsStaff,
  returnRequests,
  supportRequests,
  type FsStaff,
} from "../../mocks/fm";
import { SurfaceState } from "../../components/ui/SurfaceState";
import { DetailPanel } from "../../components/ui/DetailPanel";

type WorkItem = {
  id: string;
  label: string;
  icon: typeof CalendarDays;
};

function workItemsForStaff(staffId: string): WorkItem[] {
  const appointments = fmAppointments
    .filter((appt) => appt.staffId === staffId && appt.status === "Pending")
    .map((appt) => ({
      id: appt.id,
      label: `Lịch hẹn ${appt.type === "CHECKIN" ? "check-in" : "trả kho"} — ${appt.customerName} (${appt.unitCode}, ${appt.date})`,
      icon: CalendarDays,
    }));
  const returns = returnRequests
    .filter((request) => request.staffId === staffId && request.status === "Assigned")
    .map((request) => ({
      id: request.id,
      label: `Trả kho — ${request.customerName} (${request.unitCode})`,
      icon: Undo2,
    }));
  const support = supportRequests
    .filter((request) => request.staffId === staffId && request.status === "Assigned")
    .map((request) => ({
      id: request.id,
      label: `Hỗ trợ sự cố — khoang ${request.unitCode}`,
      icon: LifeBuoy,
    }));
  return [...appointments, ...returns, ...support];
}

export function StaffListPage() {
  const [openId, setOpenId] = useState<string | null>(null);
  const openStaff = fsStaff.find((staff) => staff.id === openId) ?? null;

  return (
    <main>
      <div className="mb-8">
        <h1 className="m-0 text-[28px] tracking-[-0.03em] text-ink">Danh sách nhân viên FS</h1>
        <p className="mt-2 text-[13px] text-muted">
          Nhân viên hiện trường được gán vào cơ sở của bạn — chỉ xem, việc gán/xoá thuộc quyền
          Admin.
        </p>
      </div>

      {fsStaff.length === 0 ? (
        <SurfaceState
          variant="empty"
          title="Chưa có FS nào được gán vào cơ sở này"
          description="Nếu cần thêm nhân sự, gửi yêu cầu qua Business Operation Manager."
        />
      ) : (
        <div className="grid gap-3">
          {fsStaff.map((staff) => {
            const workload = workItemsForStaff(staff.id).length;
            return (
              <button
                key={staff.id}
                className="flex items-start justify-between gap-5 border border-border bg-surface p-5 text-left transition-colors hover:border-brand max-[760px]:flex-col"
                onClick={() => setOpenId(staff.id)}
              >
                <div>
                  <p className="m-0 flex items-center gap-2 text-[15px] font-bold text-ink">
                    <UserRound size={15} /> {staff.name}
                  </p>
                  <p className="mb-0 mt-1.5 flex items-center gap-2 text-[13px] text-muted">
                    <Mail size={13} /> {staff.email}
                  </p>
                  <p className="mb-0 mt-1 font-mono text-[10px] uppercase tracking-[0.06em] text-muted">
                    Gán vào cơ sở từ {staff.assignedAt}
                  </p>
                </div>
                <span
                  className={`whitespace-nowrap rounded-full px-[9px] py-[6px] text-[11px] font-extrabold ${
                    workload > 0 ? "bg-brand-soft text-brand" : "bg-surface-subtle text-muted"
                  }`}
                >
                  {workload > 0 ? `${workload} việc đang xử lý` : "Không có việc"}
                </span>
              </button>
            );
          })}
        </div>
      )}

      {openStaff && <StaffDetailPanel staff={openStaff} onClose={() => setOpenId(null)} />}
    </main>
  );
}

function StaffDetailPanel({ staff, onClose }: { staff: FsStaff; onClose: () => void }) {
  const items = workItemsForStaff(staff.id);

  return (
    <DetailPanel title={staff.name} eyebrow="Nhân viên FS" onClose={onClose}>
      <div className="grid gap-2.5 text-[13px] text-muted">
        <span className="flex items-center gap-2">
          <Mail size={14} /> {staff.email}
        </span>
        <span className="flex items-center gap-2">
          <Phone size={14} /> {staff.phone}
        </span>
        <span className="font-mono text-[10px] uppercase tracking-[0.06em]">
          Gán vào cơ sở từ {staff.assignedAt}
        </span>
      </div>

      <h3 className="mb-3 mt-6 text-[13px] font-bold text-ink">
        Công việc đang được gán ({items.length})
      </h3>
      {items.length === 0 ? (
        <p className="mb-0 text-[13px] text-muted">Hiện chưa có công việc nào được gán.</p>
      ) : (
        <div className="grid gap-2">
          {items.map((item) => (
            <div
              key={item.id}
              className="flex items-center gap-2.5 border border-border bg-surface-subtle p-3 text-[12.5px] text-ink"
            >
              <item.icon size={14} className="shrink-0 text-brand" />
              {item.label}
            </div>
          ))}
        </div>
      )}
    </DetailPanel>
  );
}
