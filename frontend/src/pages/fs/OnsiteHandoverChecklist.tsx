import { Check, ChevronRight, FileText, KeyRound, ShieldCheck, UserRoundCheck } from "lucide-react";
import type { Navigate } from "../../app/types";

const steps = [
  {
    id: 1,
    title: "Xác minh danh tính",
    detail: "Đối chiếu CCCD / giấy tờ khách với đơn thuê.",
    complete: true,
    time: "08:42",
  },
  {
    id: 2,
    title: "Kiểm tra khoang",
    detail: "Ghi nhận hiện trạng khoang và chụp ảnh đối chiếu.",
    complete: true,
    time: "08:58",
  },
  {
    id: 3,
    title: "Ký hợp đồng",
    detail: "Sinh draft hợp đồng và upload ảnh bản ký.",
    complete: true,
    time: "09:14",
  },
  {
    id: 4,
    title: "Thanh toán tháng đầu",
    detail: "Xác nhận hóa đơn đầu tiên đã thanh toán thành công.",
    complete: true,
    time: "09:18",
  },
];

export function OnsiteHandoverChecklist({ navigate }: { navigate: Navigate }) {
  return (
    <main className="fs-page">
      <section className="fs-hero">
        <div className="container fs-hero__inner">
          <div>
            <p className="eyebrow">Check-in</p>
            <h1>
              Bàn giao <span>khoang.</span>
            </h1>
            <p>Tiến trình kiểm tra, xác nhận và bàn giao khóa cho khách thuê mới.</p>
          </div>
        </div>
      </section>

      <section className="container fs-page-content">
        <div className="fs-two-column">
          <div className="fs-panel">
            <div className="fs-panel-head">
              <div className="fs-panel-title">
                <UserRoundCheck size={18} />
                <strong>Checklist bàn giao</strong>
              </div>
              <span className="fs-panel-tag">Hòa tất</span>
            </div>

            <div className="fs-stepper">
              {steps.map((step) => (
                <div key={step.id} className={`fs-step ${step.complete ? "is-complete" : ""}`}>
                  <div className="fs-step-icon">
                    {step.complete ? <Check size={16} /> : <ChevronRight size={16} />}
                  </div>
                  <div className="fs-step-copy">
                    <strong>{step.title}</strong>
                    <span>{step.detail}</span>
                  </div>
                  <time>{step.time}</time>
                </div>
              ))}
            </div>

            <div className="fs-actions-row">
              <button className="button button-primary" type="button">
                <KeyRound size={16} /> Hoàn tất bàn giao
              </button>
              <button
                className="button button-secondary"
                type="button"
                onClick={() => navigate("/fs/schedule")}
              >
                <ShieldCheck size={16} /> Quay lại lịch
              </button>
            </div>
          </div>

          <aside className="fs-panel fs-panel--side">
            <div className="fs-panel-head">
              <div className="fs-panel-title">
                <FileText size={18} />
                <strong>Thông tin đơn</strong>
              </div>
            </div>

            <div className="fs-summary-grid">
              <div>
                <span>Khách hàng</span>
                <strong>Nguyễn Minh Anh</strong>
              </div>
              <div>
                <span>Khoang</span>
                <strong>A-12</strong>
              </div>
              <div>
                <span>Chi nhánh</span>
                <strong>Thảo Điền</strong>
              </div>
              <div>
                <span>Thời hạn</span>
                <strong>06 tháng</strong>
              </div>
            </div>

            <div className="fs-note-box">
              <strong>Ghi chú</strong>
              <p>Khách đã ký phê duyệt hiện trạng khoang và thanh toán thành công qua VNPay.</p>
            </div>
          </aside>
        </div>
      </section>
    </main>
  );
}
