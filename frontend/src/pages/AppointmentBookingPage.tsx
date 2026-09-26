import { Calendar, Check, Clock3, IdCard, UserRound } from "lucide-react";
import { useMemo, useState } from "react";
import type { Navigate } from "../app/types";
import { facilities } from "../mocks/catalog";
import { findDepositInvoiceByOrderId, isInvoicePaid } from "../mocks/invoices";
import { DemoNotice } from "../components/ui/DemoNotice";
import { SurfaceState } from "../components/ui/SurfaceState";

const MAX_RESCHEDULES = 2;
const TIME_SLOTS = ["08:00 – 10:00", "13:00 – 15:00", "17:00 – 19:00"];

const PRIMARY_BUTTON =
  "inline-flex min-h-11 items-center justify-center gap-2.5 rounded-sm border border-transparent bg-brand px-[18px] text-[14px] font-[750] text-background transition-colors duration-[180ms] ease hover:bg-brand-strong disabled:cursor-not-allowed disabled:opacity-60";
const SECONDARY_BUTTON =
  "inline-flex min-h-11 items-center justify-center gap-2.5 rounded-sm border border-brand bg-transparent px-[18px] text-[14px] font-[750] text-brand transition-colors duration-[180ms] ease hover:bg-brand hover:text-background disabled:cursor-not-allowed disabled:opacity-60";

function todayIso() {
  return new Date().toISOString().slice(0, 10);
}

function addDaysIso(days: number) {
  const date = new Date();
  date.setDate(date.getDate() + days);
  return date.toISOString().slice(0, 10);
}

export function AppointmentBookingPage({ navigate }: { navigate: Navigate }) {
  const orderId = useMemo(
    () => new URLSearchParams(window.location.search).get("orderId") ?? "",
    [],
  );
  const invoice = findDepositInvoiceByOrderId(orderId);
  const facility = facilities.find((item) => item.id === invoice?.facilityId);

  const [date, setDate] = useState("");
  const [slot, setSlot] = useState<string | null>(null);
  const [appointment, setAppointment] = useState<{ date: string; slot: string } | null>(null);
  const [assigned, setAssigned] = useState(false);
  const [rescheduleCount, setRescheduleCount] = useState(0);
  const [rescheduling, setRescheduling] = useState(false);

  if (!invoice || !isInvoicePaid(invoice.id)) {
    return (
      <main>
        <SurfaceState
          variant="forbidden"
          title="Chưa thể đặt lịch check-in"
          description="Bạn cần thanh toán hoá đơn đặt cọc trước khi chọn lịch hẹn check-in."
          action={{ label: "Đến trang hoá đơn", onClick: () => navigate("/invoices") }}
        />
      </main>
    );
  }

  function confirm() {
    if (!date || !slot) return;
    setAppointment({ date, slot });
    setRescheduling(false);
    setAssigned(false);
    window.setTimeout(() => setAssigned(true), 1400);
  }

  return (
    <main>
      <div className="mb-8">
        <h1 className="m-0 text-[28px] tracking-[-0.03em] text-ink">Chọn lịch hẹn check-in</h1>
        <p className="mt-2 text-[13px] text-muted">
          Đặt lịch đến cơ sở để kiểm tra khoang, ký hợp đồng và nhận bàn giao cho khoang{" "}
          {invoice.unitCode}.
        </p>
      </div>

      <div className="grid grid-cols-[1fr_320px] gap-8 max-[980px]:grid-cols-1">
        <div>
          {appointment && !rescheduling ? (
            <div className="border border-border bg-surface p-6">
              <p className="m-0 flex items-center gap-2 font-mono text-[11px] font-bold uppercase tracking-[0.08em] text-brand">
                Lịch hẹn của bạn
              </p>
              <h3 className="mb-0 mt-1.5 text-[18px] tracking-[-0.02em]">
                {appointment.date} · {appointment.slot}
              </h3>
              <div className="mt-4">
                {assigned ? (
                  <DemoNotice tone="success">
                    Đã phân công nhân viên phụ trách: Nguyễn Thành Được (FS).
                  </DemoNotice>
                ) : (
                  <DemoNotice tone="pending">Đang chờ FM phân công nhân viên phụ trách.</DemoNotice>
                )}
              </div>
              <div className="mt-5 flex items-center gap-3">
                <button
                  className={SECONDARY_BUTTON}
                  disabled={rescheduleCount >= MAX_RESCHEDULES}
                  onClick={() => {
                    setRescheduleCount((count) => count + 1);
                    setRescheduling(true);
                    setDate(appointment.date);
                    setSlot(appointment.slot);
                  }}
                >
                  Dời lịch ({MAX_RESCHEDULES - rescheduleCount} lượt còn lại)
                </button>
                <button className={SECONDARY_BUTTON} onClick={() => navigate("/my-storage")}>
                  Về Kho của tôi
                </button>
              </div>
              {rescheduleCount >= MAX_RESCHEDULES && (
                <p className="mb-0 mt-3 text-[12px] text-danger">
                  Bạn đã dời lịch tối đa {MAX_RESCHEDULES} lần. Vui lòng liên hệ hỗ trợ nếu cần đổi
                  thêm.
                </p>
              )}
            </div>
          ) : (
            <div className="border border-border bg-surface p-6">
              {rescheduling && (
                <div className="mb-5">
                  <DemoNotice tone="info">
                    Dời lịch cần báo trước ít nhất 24 giờ so với khung giờ đã chọn.
                  </DemoNotice>
                </div>
              )}
              <label className="grid gap-1.5 text-[12px] font-bold text-ink">
                <span className="flex items-center gap-[6px]">
                  <Calendar size={15} /> Chọn ngày hẹn
                </span>
                <input
                  type="date"
                  className="w-fit rounded-sm border border-border bg-surface-subtle px-3 py-2.5 text-[13px] text-ink"
                  min={todayIso()}
                  max={addDaysIso(14)}
                  value={date}
                  onChange={(event) => setDate(event.target.value)}
                />
              </label>
              <div className="mt-5">
                <span className="flex items-center gap-[6px] text-[12px] font-bold text-ink">
                  <Clock3 size={15} /> Chọn khung giờ
                </span>
                <div className="mt-2.5 flex flex-wrap gap-2.5">
                  {TIME_SLOTS.map((item) => (
                    <button
                      key={item}
                      className={`rounded-sm border px-4 py-2.5 text-[13px] font-bold transition-colors ${
                        slot === item
                          ? "border-brand bg-brand-soft text-ink"
                          : "border-border bg-transparent text-muted hover:text-ink"
                      }`}
                      onClick={() => setSlot(item)}
                    >
                      {item}
                    </button>
                  ))}
                </div>
              </div>
              <button
                className={`${PRIMARY_BUTTON} mt-6`}
                disabled={!date || !slot}
                onClick={confirm}
              >
                <Check size={16} /> Xác nhận lịch hẹn
              </button>
            </div>
          )}
        </div>

        <aside className="grid gap-5">
          <div className="border border-border bg-surface p-5">
            <p className="m-0 font-mono text-[10px] font-bold uppercase tracking-[0.08em] text-brand">
              Địa điểm
            </p>
            <h3 className="mb-0 mt-1.5 text-[15px] tracking-[-0.02em]">{facility?.name}</h3>
            <p className="mb-0 mt-2 text-[12px] leading-[1.6] text-muted">{facility?.address}</p>
            <p className="mb-0 mt-1 text-[12px] leading-[1.6] text-muted">{facility?.hours}</p>
          </div>
          <div className="border border-border bg-surface p-5">
            <p className="m-0 flex items-center gap-[6px] font-mono text-[10px] font-bold uppercase tracking-[0.08em] text-brand">
              <IdCard size={14} /> Cần chuẩn bị
            </p>
            <ul className="m-0 mt-2.5 grid gap-2 pl-0 text-[12px] text-muted">
              <li className="flex items-start gap-2">
                <Check size={14} className="mt-0.5 shrink-0 text-brand" /> CCCD hoặc hộ chiếu bản
                gốc
              </li>
              <li className="flex items-start gap-2">
                <UserRound size={14} className="mt-0.5 shrink-0 text-brand" /> Có mặt đúng người
                đứng tên hợp đồng
              </li>
            </ul>
          </div>
        </aside>
      </div>
    </main>
  );
}
