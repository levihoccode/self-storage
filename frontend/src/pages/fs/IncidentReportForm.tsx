import { AlertTriangle, Camera, Send, ShieldAlert } from "lucide-react";
import type { Navigate } from "../../app/types";

export function IncidentReportForm({ navigate }: { navigate: Navigate }) {
  return (
    <main className="fs-page">
      <section className="fs-hero">
        <div className="container fs-hero__inner">
          <div>
            <p className="eyebrow">Incident reporting</p>
            <h1>
              Ghi nhận <span>sự cố.</span>
            </h1>
            <p>
              FS có thể chủ động báo cáo sự cố phát sinh tại khoang hoặc khu vực làm việc ngay trong
              buổi.
            </p>
          </div>
        </div>
      </section>

      <section className="container fs-page-content">
        <div className="fs-panel fs-panel--wide">
          <div className="fs-panel-head">
            <div className="fs-panel-title">
              <ShieldAlert size={18} />
              <strong>Form báo cáo sự cố</strong>
            </div>
            <span className="fs-panel-tag">Chưa gửi</span>
          </div>

          <div className="fs-form-grid">
            <label className="fs-field">
              <span>Khoang / khu vực</span>
              <select defaultValue="A-12">
                <option value="A-12">A-12 - Kho Mộc Thảo Điền</option>
                <option value="B-05">B-05 - Kho Mộc Quận 7</option>
                <option value="C-03">C-03 - Kho Mộc Thảo Điền</option>
              </select>
            </label>

            <label className="fs-field">
              <span>Loại sự cố</span>
              <select defaultValue="LostKey">
                <option value="LostKey">Mất chìa / mất khóa</option>
                <option value="UnitDamage">Hư hỏng khoang</option>
                <option value="AccessCode">Mã truy cập lỗi</option>
                <option value="Other">Khác</option>
              </select>
            </label>

            <label className="fs-field fs-field--full">
              <span>Mô tả</span>
              <textarea
                rows={6}
                defaultValue="Khóa cửa khoang bị kẹt khi khách đang kiểm tra. Hệ thống cần hỗ trợ ngay trước khi bàn giao tiếp tục."
              />
            </label>

            <label className="fs-field fs-field--full">
              <span>Hình ảnh / tệp đính kèm</span>
              <div className="fs-upload-box">
                <Camera size={18} />
                <span>Chưa có ảnh đính kèm</span>
              </div>
            </label>
          </div>

          <div className="fs-actions-row">
            <button className="button button-primary" type="button">
              <Send size={16} /> Gửi ghi nhận sự cố
            </button>
            <button
              className="button button-secondary"
              type="button"
              onClick={() => navigate("/fs/support-requests")}
            >
              <AlertTriangle size={16} /> Xem queue hỗ trợ
            </button>
          </div>
        </div>
      </section>
    </main>
  );
}
