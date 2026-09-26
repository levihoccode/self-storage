import { View } from "./types";

export function viewFromLocation(): View {
  const path = window.location.pathname;
  if (path === "/units") return "units";
  if (path === "/login") return "login";
  if (path === "/register") return "register";
  if (path === "/verify-email") return "verify";
  if (path === "/rental-requests/new") return "request";
  if (path === "/my-storage") return "my-storage";
  if (path === "/notifications" || path === "/notifications/") return "notifications";
  if (path === "/admin/accounts" || path === "/admin/accounts/") return "admin-accounts";
  if (path === "/admin/staff-requests" || path === "/admin/staff-requests/") return "admin-staff-requests";
  if (path === "/admin/rbac" || path === "/admin/rbac/") return "admin-rbac";
  if (path === "/admin/login-history" || path === "/admin/login-history/") return "admin-login-history";
  if (path === "/admin/audit-log" || path === "/admin/audit-log/") return "admin-audit-log";
  if (path === "/fs/schedule" || path === "/fs/schedule/") return "fs-schedule";
  if (path === "/fs/support-requests" || path === "/fs/support-requests/") return "fs-support";
  if (path === "/fs/incidents/new" || path === "/fs/incidents/new/") return "fs-incidents";
  if (path.startsWith("/fs/appointments/")) {
    if (path.includes("/handover")) return "fs-handover";
    if (path.includes("/return")) return "fs-return";
  }
  if (path === "/bom" || path === "/bom/") return "bom-policies";
  if (path === "/bom/policies" || path === "/bom/policies/") return "bom-policies";
  if (path === "/bom/fees" || path === "/bom/fees/") return "bom-fees";
  if (path === "/bom/discounts" || path === "/bom/discounts/") return "bom-discounts";
  if (path === "/bom/revenue" || path === "/bom/revenue/") return "bom-revenue";
  if (path === "/bom/facilities" || path === "/bom/facilities/") return "bom-facilities";
  if (path === "/bom/staff-requests" || path === "/bom/staff-requests/") return "bom-staff-requests";
  if (path === "/bom/unit-types" || path === "/bom/unit-types/") return "bom-unit-types";
  if (path === "/bom/reports" || path === "/bom/reports/") return "bom-reports";
  return "home";
}
