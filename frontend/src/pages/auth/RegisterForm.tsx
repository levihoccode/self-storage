import { FormEvent, useState } from "react";
import { ArrowRight, Box, LockKeyhole, Mail, Phone } from "lucide-react";
import { Navigate, Notice } from "../../app/types";
import { DemoNotice } from "../../components/ui/DemoNotice";
import { FormField } from "../../components/ui/FormField";

export function RegisterForm({ navigate }: { navigate: Navigate }) {
  const [notice, setNotice] = useState<Notice>(null);
  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    if (!data.get("name") || !data.get("email") || !data.get("password")) {
      setNotice({ tone: "error", message: "Vui lòng hoàn thiện các trường bắt buộc." });
      return;
    }
    setNotice({
      tone: "success",
      message: "Kiểm tra email để hoàn tất đăng ký.",
    });
  }
  return (
    <>
      <div className="mb-6">
        <h1 className="m-0 text-[32px] leading-tight tracking-[-0.045em] text-ink">
          Tạo tài khoản
        </h1>
        <p className="mt-3 text-[13px] text-muted">
          Đăng ký Customer để theo dõi nhu cầu lưu trữ, hợp đồng và hóa đơn.
        </p>
      </div>
      <form className="grid gap-0" onSubmit={submit}>
        <FormField
          label="Họ và tên / doanh nghiệp"
          name="name"
          required
          placeholder="Công ty ABC / Nguyễn Minh An"
          icon={<Box size={17} />}
        />
        <FormField
          label="Email"
          name="email"
          type="email"
          required
          placeholder="ban@example.com"
          icon={<Mail size={17} />}
        />
        <FormField
          label="Số điện thoại"
          name="phone"
          placeholder="090 123 4567"
          icon={<Phone size={17} />}
        />
        <FormField
          label="Mật khẩu"
          name="password"
          type="password"
          required
          placeholder="Tối thiểu 8 ký tự"
          icon={<LockKeyhole size={17} />}
        />
        <label className="mb-[18px] flex items-start gap-2 text-[11px] text-muted">
          <input className="mt-px h-[15px] w-[15px] accent-brand" type="checkbox" required />
          <span>Tôi đồng ý với điều khoản sử dụng của Kho Mộc.</span>
        </label>
        <button
          className="inline-flex min-h-11 w-full items-center justify-center gap-2 rounded-sm border-0 bg-brand px-[18px] text-label text-background transition-colors hover:bg-brand-strong"
          type="submit"
        >
          Tạo tài khoản <ArrowRight size={17} />
        </button>
        {notice && <DemoNotice tone={notice.tone}>{notice.message}</DemoNotice>}
      </form>
      <p className="mt-5 text-center text-xs text-muted">
        Đã có tài khoản?{" "}
        <button
          className="border-0 bg-transparent p-0 font-bold text-brand"
          onClick={() => navigate("/login")}
        >
          Đăng nhập
        </button>
      </p>
    </>
  );
}
