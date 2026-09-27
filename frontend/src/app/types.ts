export type View =
  | "home"
  | "units"
  | "login"
  | "register"
  | "verify"
  | "request"
  | "my-storage"
  | "fm-rental-requests";
export type Navigate = (path: string) => void;
export type NoticeTone = "info" | "success" | "error" | "pending";
export type Notice = { tone: NoticeTone; message: string } | null;
