import { apiRequest } from "./api";
import { authGateway } from "./auth";

export type AppointmentType = "CHECKIN" | "HANDOVER" | "RETURN";
export type AppointmentStatus = "Pending" | "Done" | "Canceled";

/** Một lịch hẹn trên màn lịch theo ngày của FM — `GET /api/fm/appointments`. */
export type FmAppointment = {
  id: number;
  type: AppointmentType;
  status: AppointmentStatus;
  date: string;
  startedAt: string | null;
  endAt: string | null;
  orderId: number | null;
  orderStatus: string | null;
  unitCode: string | null;
  customerEmail: string;
  staffId: number | null;
  staffEmail: string | null;
};

/** FS đang hoạt động của cơ sở — `GET /api/fm/facilities/{id}/staff`. */
export type FacilityStaff = {
  id: number;
  email: string;
};

function accessToken(): string | undefined {
  return authGateway.getSession()?.token;
}

export function fetchAppointmentsOfDay(facilityId: number, date: string): Promise<FmAppointment[]> {
  const params = new URLSearchParams({ facilityId: String(facilityId), date });
  return apiRequest<FmAppointment[]>(`/api/fm/appointments?${params}`, {
    accessToken: accessToken(),
  });
}

export function fetchFacilityStaff(facilityId: number): Promise<FacilityStaff[]> {
  return apiRequest<FacilityStaff[]>(`/api/fm/facilities/${facilityId}/staff`, {
    accessToken: accessToken(),
  });
}

/** 409 khi lịch đã có FS, không còn Pending hoặc đơn không ở Scheduled; 400 khi FS khác cơ sở. */
export function assignAppointmentStaff(
  appointmentId: number,
  staffId: number,
): Promise<FmAppointment> {
  return apiRequest<FmAppointment>(`/api/fm/appointments/${appointmentId}/staff`, {
    method: "POST",
    body: { staffId },
    accessToken: accessToken(),
  });
}

/** Ngày hôm nay theo giờ máy, dạng ISO `yyyy-MM-dd` (không dùng toISOString vì lệch sang UTC). */
export function todayIsoDate(): string {
  const now = new Date();
  const pad = (value: number) => String(value).padStart(2, "0");
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`;
}

/** `2026-10-27` → `27/10/2026`. */
export function formatIsoDate(isoDate: string): string {
  const [year, month, day] = isoDate.split("-");
  return `${day}/${month}/${year}`;
}

/** Khung giờ hẹn theo giờ máy, vd `08:00 – 10:00`; chưa có giờ thì báo rõ. */
export function formatTimeSlot(startedAt: string | null, endAt: string | null): string {
  if (!startedAt) return "Chưa có khung giờ";
  const time = (iso: string) =>
    new Date(iso).toLocaleTimeString("vi-VN", {
      hour: "2-digit",
      minute: "2-digit",
      hour12: false,
    });
  return endAt ? `${time(startedAt)} – ${time(endAt)}` : time(startedAt);
}
