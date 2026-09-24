import { FormEvent, useState } from "react";
import { ArrowRight, Eye, EyeOff, LockKeyhole, Mail } from "lucide-react";
import { Navigate, Notice } from "../../app/types";
import { authGateway, DEMO_EMAIL, DEMO_PASSWORD } from "../../app/auth";
import { DemoNotice } from "../../components/ui/DemoNotice";
import {
  FIELD_GROUP,
  FIELD_LABEL,
  FormField,
  INPUT_ELEMENT,
  INPUT_WRAP,
} from "../../components/ui/FormField";

export function LoginForm({ navigate }: { navigate: Navigate }) {
  const [notice, setNotice] = useState<Notice>(null);
  const [showPassword, setShowPassword] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    const email = String(data.get("email") || "");
    const password = String(data.get("password") || "");
    if (!email || !password) {
      setNotice({ tone: "error", message: "Vui lòng nhập email và mật khẩu để tiếp tục." });
      return;
    }
    setIsSubmitting(true);
    const result = await authGateway.login(email, password);
    setIsSubmitting(false);
    if (!result.ok) {
      setNotice({ tone: "error", message: result.message });
      return;
    }
    navigate("/my-storage");
  }
  return (
    <>
      <div className="auth-heading">
        <h1>Đăng nhập</h1>
        <p>Quản lý nhu cầu lưu trữ của bạn tại Kho Mộc.</p>
      </div>
      <form className="auth-form" onSubmit={submit}>
        <FormField
          label="Email"
          name="email"
          type="email"
          required
          placeholder="ban@example.com"
          icon={<Mail size={17} />}
        />
        <div className={FIELD_GROUP}>
          <div className="flex items-center justify-between gap-4">
            <label className={FIELD_LABEL} htmlFor="password">
              Mật khẩu
            </label>
            <button
              type="button"
              className="self-start border-0 bg-transparent p-0 text-[10px] font-extrabold text-brand"
              onClick={() =>
                setNotice({
                  tone: "info",
                  message: "Nhập email để tiếp tục đặt lại mật khẩu.",
                })
              }
            >
              Quên mật khẩu?
            </button>
          </div>
          <div className={INPUT_WRAP}>
            <LockKeyhole size={17} />
            <input
              id="password"
              name="password"
              type={showPassword ? "text" : "password"}
              placeholder="Nhập mật khẩu"
              autoComplete="current-password"
              className={INPUT_ELEMENT}
            />
            <button
              type="button"
              className="grid place-items-center border-0 bg-transparent p-[3px] text-muted"
              onClick={() => setShowPassword((current) => !current)}
              aria-label={showPassword ? "Ẩn mật khẩu" : "Hiện mật khẩu"}
            >
              {showPassword ? <EyeOff size={17} /> : <Eye size={17} />}
            </button>
          </div>
        </div>
        <button className="button button-primary button-full" type="submit" disabled={isSubmitting}>
          {isSubmitting ? "Đang kiểm tra…" : "Đăng nhập"}{" "}
          {!isSubmitting && <ArrowRight size={17} />}
        </button>
        <div className="demo-credentials">
          <strong>Tài khoản demo</strong>
          <span>Email: {DEMO_EMAIL}</span>
          <span>Mật khẩu: {DEMO_PASSWORD}</span>
        </div>
        {notice && <DemoNotice tone={notice.tone}>{notice.message}</DemoNotice>}
      </form>
      <p className="auth-switch">
        Bạn chưa có tài khoản?{" "}
        <button onClick={() => navigate("/register")}>Đăng ký miễn phí</button>
      </p>
    </>
  );
}
