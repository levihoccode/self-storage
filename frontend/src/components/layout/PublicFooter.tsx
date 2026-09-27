import { Warehouse } from "lucide-react";
import { Navigate } from "../../app/types";

export function PublicFooter({ navigate }: { navigate: Navigate }) {
  return (
    <footer className="public-footer">
      <div className="container footer-shell">
        <div className="footer-cta-row">
          <div className="footer-cta-copy">
            <h3>Đã biết quy mô?</h3>
            <p>Gửi nhu cầu lưu trữ khi bạn đã sẵn sàng.</p>
          </div>

          <button type="button" className="footer-cta-button" onClick={() => navigate("/rental-requests/new")}>
            Gửi nhu cầu lưu trữ <span aria-hidden="true">→</span>
          </button>
        </div>

        <div className="footer-meta-row">
          <div className="footer-brand-block">
            <button className="brand footer-brand-button" onClick={() => navigate("/")}>
              <span className="brand-mark footer-brand-mark">
                <Warehouse size={18} strokeWidth={2.25} />
              </span>
              <span>
                kho<span className="brand-dot">.</span>mộc
              </span>
            </button>
          </div>

          <div className="footer-link-groups">
            <div className="footer-link-group">
              <strong>Khám phá</strong>
              <button type="button" onClick={() => navigate("/units")}>Phương án kho</button>
              <a href="/#how-it-works">Quy trình</a>
              <button type="button" onClick={() => navigate("/rental-requests/new")}>Gửi nhu cầu</button>
            </div>

            <div className="footer-link-group">
              <strong>Hỗ trợ</strong>
              <a href="mailto:hello@khomoc.example">Email cho Kho Mộc</a>
              <a href="tel:+842812345678">02812345678</a>
              <span>TP. Hồ Chí Minh</span>
            </div>
          </div>
        </div>

        <div className="footer-bottom">
          <span>© 2026 Kho Mộc.</span>
        </div>
      </div>
    </footer>
  );
}
