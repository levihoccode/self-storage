import { ArrowRight, Mail } from "lucide-react";
import { Navigate } from "../../app/types";
import { Button } from "../../components/ui/Button";

export function VerifyPanel({ navigate }: { navigate: Navigate }) {
  return (
    <div className="text-center">
      <span className="mx-auto mb-5 grid h-14 w-14 place-items-center bg-brand text-signal-ink">
        <Mail size={26} aria-hidden="true" />
      </span>
      <h1 className="m-0 text-section text-ink">
        Kiểm tra hộp thư
        <br />
        của bạn nhé.
      </h1>
      <p className="mx-auto mt-3 max-w-[38ch] text-small text-muted">
        Mở liên kết trong email để hoàn tất đăng ký, sau đó quay lại đăng nhập vào Kho Mộc.
      </p>
      <div className="mt-6 flex items-center justify-center gap-5 max-[760px]:flex-col">
        <Button onClick={() => navigate("/login")}>
          Đến trang đăng nhập <ArrowRight size={16} />
        </Button>
        <button
          className="inline-flex items-center gap-1 border-0 bg-transparent p-0 text-small font-semibold text-accent hover:text-ink"
          onClick={() => navigate("/units")}
        >
          Xem phương án kho <ArrowRight size={16} />
        </button>
      </div>
    </div>
  );
}
