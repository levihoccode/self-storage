import { ArrowRight, CalendarDays, Clock3, MapPin, Search } from "lucide-react";
import { useMemo, useState } from "react";
import type { Navigate } from "../../app/types";

type AppointmentType = "CHECKIN" | "RETURN";
type AppointmentStatus = "Scheduled" | "Arrived" | "Pending" | "Late";

type ScheduleItem = {
  id: string;
  customer: string;
  unit: string;
  facility: string;
  type: AppointmentType;
  time: string;
  status: AppointmentStatus;
  location: string;
  date: string;
};

const appointments: ScheduleItem[] = [
  {
    id: "appt-101",
    customer: "Nguyễn Minh Anh",
    unit: "A-12",
    facility: "Kho Mộc Thảo Điền",
    type: "CHECKIN",
    time: "08:30 - 09:00",
    status: "Scheduled",
    location: "Gần cổng số 2",
    date: "2026-09-26",
  },
  {
    id: "appt-102",
    customer: "Trần Thị Bích",
    unit: "B-08",
    facility: "Kho Mộc Thảo Điền",
    type: "RETURN",
    time: "09:30 - 10:00",
    status: "Late",
    location: "Khu 2 - lối vào phía nam",
    date: "2026-09-26",
  },
  {
    id: "appt-103",
    customer: "Phạm Hoàng Nam",
    unit: "C-05",
    facility: "Kho Mộc Quận 7",
    type: "CHECKIN",
    time: "11:00 - 11:30",
    status: "Arrived",
    location: "Khu 1 - tầng trệt",
    date: "2026-09-26",
  },
  {
    id: "appt-104",
    customer: "Lê Quỳnh Như",
    unit: "D-03",
    facility: "Kho Mộc Quận 7",
    type: "RETURN",
    time: "14:00 - 14:30",
    status: "Pending",
    location: "Khu 3 - mặt tiền",
    date: "2026-09-27",
  },
];

export function DailySchedule({ navigate }: { navigate: Navigate }) {
  const [selectedDate, setSelectedDate] = useState("2026-09-26");
  const [query, setQuery] = useState("");

  const filteredAppointments = useMemo(() => {
    const keyword = query.trim().toLowerCase();
    return appointments.filter((item) => {
      const sameDate = item.date === selectedDate;
      const matchesQuery =
        !keyword ||
        item.customer.toLowerCase().includes(keyword) ||
        item.unit.toLowerCase().includes(keyword) ||
        item.facility.toLowerCase().includes(keyword);
      return sameDate && matchesQuery;
    });
  }, [query, selectedDate]);

  return (
    <main className="fs-page">
      <section className="fs-hero">
        <div className="container fs-hero__inner">
          <div>
            <p className="eyebrow">Facility staff</p>
            <h1>
              Lịch làm <span>việc.</span>
            </h1>
            <p>Theo dõi các cuộc hẹn check-in / return của bạn trong ngày và tiến hành xử lý ngay.</p>
          </div>
        </div>
      </section>

      <section className="container fs-page-content">
        <div className="fs-panel-toolbar">
          <div className="fs-toolbar-block">
            <CalendarDays size={18} />
            <strong>Ngày làm việc</strong>
          </div>
          <div className="fs-toolbar-actions">
            <label className="fs-search-field">
              <Search size={14} />
              <input value={query} onChange={(e) => setQuery(e.target.value)} placeholder="Tìm khách, khoang, chi nhánh" />
            </label>
            <input type="date" value={selectedDate} onChange={(e) => setSelectedDate(e.target.value)} />
          </div>
        </div>

        <div className="fs-card-grid">
          {filteredAppointments.map((item) => (
            <article key={item.id} className="fs-card">
              <div className="fs-card-header">
                <div>
                  <p className="eyebrow">{item.type}</p>
                  <h3>{item.customer}</h3>
                </div>
                <span className={`fs-badge fs-badge--${item.status.toLowerCase()}`}>{item.status}</span>
              </div>

              <div className="fs-meta-list">
                <div>
                  <span>Khoang</span>
                  <strong>{item.unit}</strong>
                </div>
                <div>
                  <span>Chi nhánh</span>
                  <strong>{item.facility}</strong>
                </div>
                <div>
                  <span>Khung giờ</span>
                  <strong>{item.time}</strong>
                </div>
              </div>

              <div className="fs-address-row">
                <MapPin size={15} />
                <span>{item.location}</span>
              </div>

              <div className="fs-card-actions">
                <button
                  className="button button-primary"
                  type="button"
                  onClick={() =>
                    navigate(item.type === "CHECKIN" ? `/fs/appointments/${item.id}/handover` : `/fs/appointments/${item.id}/return`)
                  }
                >
                  {item.type === "CHECKIN" ? "Bàn giao khoang" : "Kiểm tra trả kho"}
                  <ArrowRight size={16} />
                </button>
                <button className="button button-secondary" type="button">
                  <Clock3 size={16} />
                  {item.status === "Late" ? "Chưa đến" : "Đã xác nhận"}
                </button>
              </div>
            </article>
          ))}
        </div>

        {!filteredAppointments.length && (
          <div className="empty-state">Không có lịch hẹn nào phù hợp cho ngày đã chọn.</div>
        )}
      </section>
    </main>
  );
}
