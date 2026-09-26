import { ArrowRight, Mail } from "lucide-react";
import { Navigate } from "../../app/types";

export function VerifyPanel({ navigate }: { navigate: Navigate }) {
  return (
    <div className="text-center">
      <span className="mx-auto mb-1 grid h-14 w-14 place-items-center rounded-sm bg-brand-soft text-brand">
        <Mail size={26} />
      </span>
      <h1 className="m-0 text-[41px] leading-[1.06] tracking-[-0.08em] text-ink">
        Kiểm tra hộp thư
        <br />
        <span>của bạn nhé.</span>
      </h1>
      <p className="my-5 text-[13px] text-muted">
        Mở liên kết trong email để hoàn tất đăng ký, sau đó quay lại đăng nhập vào Kho Mộc.
      </p>
      <div className="mt-6 flex items-center justify-center gap-[22px] max-[760px]:flex-col">
        <button
          className="inline-flex min-h-11 items-center justify-center gap-2 rounded-sm border-0 bg-brand px-[18px] text-label text-background transition-colors hover:bg-brand-strong"
          onClick={() => navigate("/login")}
        >
          Đến trang đăng nhập <ArrowRight size={16} />
        </button>
        <button
          className="inline-flex items-center gap-1 border-0 bg-transparent p-0 text-xs font-bold text-brand hover:gap-2"
          onClick={() => navigate("/units")}
        >
          Xem phương án kho <ArrowRight size={16} />
        </button>
      </div>
    </div>
  );
}
