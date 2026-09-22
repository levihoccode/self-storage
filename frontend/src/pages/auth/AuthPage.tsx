import { ArrowLeft } from "lucide-react";
import { Navigate, View } from "../../app/types";
import { Brand } from "../../components/ui/Brand";
import type { Theme } from "../../components/ui/ThemeToggle";
import { ThemeToggle } from "../../components/ui/ThemeToggle";
import { LoginForm } from "./LoginForm";
import { RegisterForm } from "./RegisterForm";
import { VerifyPanel } from "./VerifyPanel";

export function AuthPage({
  view,
  navigate,
  theme,
  onThemeToggle,
}: {
  view: View;
  navigate: Navigate;
  theme: Theme;
  onThemeToggle: () => void;
}) {
  const mode = view === "register" ? "register" : view === "verify" ? "verify" : "login";
  return (
    <div className="auth-page">
      <div className="auth-shell">
        <div className="auth-context">
          <div className="auth-theme-toggle">
            <ThemeToggle theme={theme} onToggle={onThemeToggle} />
          </div>
          <Brand className="auth-brand" onClick={() => navigate("/")} />
          <div className="auth-panel-copy">
            <h1>
              Quản lý nhu cầu kho
              <br />
              <em>trong một nơi.</em>
            </h1>
            <p>Theo dõi yêu cầu lưu trữ, hợp đồng và hóa đơn.</p>
          </div>
        </div>
        <div className="auth-form-panel">
          <div className="auth-form-wrap">
            <button className="auth-back" onClick={() => navigate("/")}>
              <ArrowLeft size={16} /> Trang chủ
            </button>
            {mode === "login" && <LoginForm navigate={navigate} />}
            {mode === "register" && <RegisterForm navigate={navigate} />}
            {mode === "verify" && <VerifyPanel navigate={navigate} />}
          </div>
        </div>
      </div>
    </div>
  );
}
