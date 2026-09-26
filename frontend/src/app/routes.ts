import { View } from "./types";

export function viewFromLocation(): View {
  const path = window.location.pathname;
  if (path === "/units") return "units";
  if (path === "/login") return "login";
  if (path === "/register") return "register";
  if (path === "/verify-email") return "verify";
  if (path === "/rental-requests/new") return "request";
  if (path === "/my-storage") return "my-storage";
  return "home";
}
