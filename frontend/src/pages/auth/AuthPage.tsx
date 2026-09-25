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
    <div className="block min-h-screen bg-background px-6 pb-20 pt-16 max-[760px]:px-4 max-[760px]:pb-[52px] max-[760px]:pt-[28px]">
      <div className="grid w-[min(1060px,100%)] grid-cols-[minmax(0,0.85fr)_minmax(390px,1.15fr)] gap-[72px] max-[980px]:gap-[42px] max-[760px]:block mx-auto">
        <div className="flex min-h-[620px] flex-col border-t-4 border-brand pb-5 pt-6 max-[760px]:min-h-0 max-[760px]:pb-0 max-[760px]:pt-[18px]">
          <div className="mb-[18px] flex justify-end">
            <ThemeToggle theme={theme} onToggle={onThemeToggle} />
          </div>
          <Brand
            className="relative z-[1] self-start"
            dotClassName="text-[#9ce0d3]"
            onClick={() => navigate("/")}
          />
          <div className="my-auto max-w-[420px] max-[760px]:mb-9 max-[760px]:mt-[50px]">
            <h1 className="m-0 text-[clamp(42px,5vw,60px)] leading-[1.06] tracking-[-0.045em] text-ink max-[760px]:text-[42px]">
              Quản lý nhu cầu kho
              <br />
              <em className="not-italic text-brand">trong một nơi.</em>
            </h1>
            <p className="mt-6 max-w-[380px] text-[14px] leading-[1.7] text-muted max-[760px]:hidden">
              Theo dõi yêu cầu lưu trữ, hợp đồng và hóa đơn.
            </p>
          </div>
        </div>
        <div className="block bg-transparent pt-6 max-[760px]:pt-[30px]">
          <div className="mx-auto w-[min(430px,100%)] rounded-md border border-border bg-surface p-8 shadow-none max-[760px]:p-6">
            <button
              className="mb-[54px] inline-flex items-center gap-2 border-0 bg-transparent p-0 text-[12px] font-bold text-muted hover:text-brand max-[760px]:mb-[42px]"
              onClick={() => navigate("/")}
            >
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
