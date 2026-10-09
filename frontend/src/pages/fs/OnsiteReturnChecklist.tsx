import {
  AlertTriangle,
  ArrowRight,
  CheckCircle2,
  ClipboardList,
  DollarSign,
  KeyRound,
} from "lucide-react";
import type { Navigate } from "../../app/types";

const inspections = [
  { label: "Khoang còn trống", value: "Đạt", ok: true },
  { label: "Vệ sinh", value: "Tốt", ok: true },
  { label: "Hư hỏng", value: "Không phát hiện", ok: true },
  { label: "Khóa trả lại", value: "1/1", ok: true },
];

const fees = [
  { label: "Phí vệ sinh", value: "0đ" },
  { label: "Phí hư hỏng", value: "0đ" },
  { label: "Cọc đã thu", value: "15.000.000đ" },
  { label: "Cần hoàn lại", value: "15.000.000đ" },
];

export function OnsiteReturnChecklist({ navigate }: { navigate: Navigate }) {
  return (
    <main className="fs-page">
      <section className="fs-hero">
        <div className="container fs-hero__inner">
          <div>
            <p className="eyebrow">Return</p>
            <h1>
              Trả kho <span>on-site.</span>
            </h1>
            <p>
              Kiểm tra hiện trạng khoang, tính toán phí phát sinh và chốt biên bản bàn giao trả kho.
            </p>
          </div>
        </div>
      </section>

      <section className="container fs-page-content">
        <div className="fs-two-column">
          <div className="fs-panel">
            <div className="fs-panel-head">
              <div className="fs-panel-title">
                <ClipboardList size={18} />
                <strong>Thông tin kiểm tra</strong>
              </div>
              <span className="fs-panel-tag">Đạt yêu cầu</span>
            </div>

            <div className="fs-check-list">
              {inspections.map((item) => (
                <div key={item.label} className="fs-check-row">
                  <div className={`fs-check-icon ${item.ok ? "is-ok" : "is-warning"}`}>
                    {item.ok ? <CheckCircle2 size={16} /> : <AlertTriangle size={16} />}
                  </div>
                  <div className="fs-check-copy">
                    <strong>{item.label}</strong>
                  </div>
                  <span>{item.value}</span>
                </div>
              ))}
            </div>

            <div className="fs-actions-row">
              <button className="button button-primary" type="button">
                <KeyRound size={16} /> Chốt biên bản trả kho
              </button>
              <button
                className="button button-secondary"
                type="button"
                onClick={() => navigate("/fs/schedule")}
              >
                <ArrowRight size={16} /> Quay lại lịch
              </button>
            </div>
          </div>

          <aside className="fs-panel fs-panel--side">
            <div className="fs-panel-head">
              <div className="fs-panel-title">
                <DollarSign size={18} />
                <strong>Bảng đối trừ cọc</strong>
              </div>
            </div>

            <div className="fs-fee-list">
              {fees.map((fee) => (
                <div key={fee.label} className="fs-fee-row">
                  <span>{fee.label}</span>
                  <strong>{fee.value}</strong>
                </div>
              ))}
            </div>

            <div className="fs-note-box">
              <strong>Ghi chú</strong>
              <p>
                Đối trừ cọc dư sẽ được FM xử lý hoàn trả theo quy trình thủ công sau khi chốt biên
                bản.
              </p>
            </div>
          </aside>
        </div>
      </section>
    </main>
  );
}
