import { Warehouse } from "lucide-react";
import { Navigate } from "../../app/types";

export function PublicFooter({ navigate }: { navigate: Navigate }) {
  return (
    <footer className="public-footer">
      <div className="container footer-main">
        <div className="footer-brand">
          <button className="brand" onClick={() => navigate("/")}>
            <span className="brand-mark">
              <Warehouse size={18} strokeWidth={2.25} />
            </span>
            <span>
              kho<span className="brand-dot">.</span>mộc
            </span>
          </button>
        </div>
        <div className="footer-links">
          <div>
            <strong>Khám phá</strong>
            <button onClick={() => navigate("/units")}>Phương án kho</button>
            <a href="/#how-it-works">Quy trình</a>
            <button onClick={() => navigate("/rental-requests/new")}>Gửi nhu cầu</button>
          </div>
          <div>
            <strong>Hỗ trợ</strong>
            <a href="mailto:hello@khomoc.example">Email cho Kho Mộc</a>
            <a href="tel:+842812345678">028 1234 5678</a>
            <span>TP. Hồ Chí Minh</span>
          </div>
        </div>
      </div>
      <div className="container footer-bottom">
        <span>© 2026 Kho Mộc.</span>
      </div>
    </footer>
  );
}
