import { ArrowRight, Mail } from "lucide-react";
import { Navigate } from "../../app/types";

export function VerifyPanel({ navigate }: { navigate: Navigate }) {
  return (
    <div className="verify-panel">
      <span className="verify-icon">
        <Mail size={26} />
      </span>
      <h1>
        Kiểm tra hộp thư
        <br />
        <span>của bạn nhé.</span>
      </h1>
      <p>
        Mở liên kết trong email để hoàn tất đăng ký. Sau khi xác minh, khách hàng có thể đăng nhập và tiếp tục
        vào kho của mình.
      </p>
      <div className="verify-actions">
        <button className="button button-primary" onClick={() => navigate("/login")}>
          Đến trang đăng nhập <ArrowRight size={16} />
        </button>
        <button className="text-link" onClick={() => navigate("/units")}>
          Xem phương án kho <ArrowRight size={16} />
        </button>
      </div>
    </div>
  );
}
