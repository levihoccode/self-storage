import { FormEvent, useState } from "react";
import { ArrowLeft, ArrowRight, LockKeyhole } from "lucide-react";
import { Navigate } from "../app/types";
import { facilities, unitTypes } from "../mocks/catalog";
import { FormField, SelectField } from "../components/ui/FormField";
import { SuccessState } from "../components/ui/SuccessState";

export function RequestPage({ navigate }: { navigate: Navigate }) {
  const [submitted, setSubmitted] = useState(false);
  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSubmitted(true);
  }
  if (submitted)
    return (
      <section className="form-page">
        <div className="container narrow-container">
          <SuccessState
            title="Đã nhận nhu cầu."
            description="Đội ngũ vận hành sẽ kiểm tra phương án và phản hồi qua email hoặc số điện thoại."
            action={
              <button className="button button-primary" onClick={() => navigate("/units")}>
                Xem lại phương án kho <ArrowRight size={16} />
              </button>
            }
          />
        </div>
      </section>
    );
  return (
    <section className="form-page">
      <div className="container request-layout">
        <div className="form-intro">
          <button className="back-link" onClick={() => navigate("/units")}>
            <ArrowLeft size={16} /> Quay lại phương án kho
          </button>
          <h1>Gửi nhu cầu lưu trữ.</h1>
          <p>Chọn điểm kho, quy mô và thời gian bắt đầu.</p>
        </div>
        <form className="request-form" onSubmit={submit}>
          <div className="form-card">
            <div className="form-card-heading">
              <div>
                <h2>Thông tin lưu trữ</h2>
                <p>Các trường có dấu * là bắt buộc.</p>
              </div>
            </div>
            <div className="form-grid">
              <FormField
                label="Tên doanh nghiệp / người liên hệ"
                name="name"
                required
                placeholder="Công ty ABC / Nguyễn Minh An"
              />
              <FormField
                label="Email liên hệ"
                name="email"
                required
                type="email"
                placeholder="ban@example.com"
              />
              <FormField label="Số điện thoại" name="phone" required placeholder="090 123 4567" />
              <SelectField
                label="Điểm kho mong muốn"
                name="facility"
                required
                options={facilities.map((item) => ({ value: item.id, label: item.name }))}
              />
              <SelectField
                label="Quy mô kho dự kiến"
                name="unitType"
                required
                options={unitTypes.map((item) => ({
                  value: item.id,
                  label: `${item.name} · ${item.capacity}`,
                }))}
              />
              <FormField label="Thời điểm bắt đầu" name="startDate" required type="date" />
              <FormField
                label="Thời hạn dự kiến"
                name="period"
                required
                type="number"
                placeholder="Ví dụ: 6"
                suffix="tháng"
              />
            </div>
            <div className="form-submit-row">
              <span>
                <LockKeyhole size={14} /> Chưa cần thanh toán ở bước này
              </span>
              <button className="button button-primary" type="submit">
                Gửi nhu cầu <ArrowRight size={16} />
              </button>
            </div>
          </div>
        </form>
      </div>
    </section>
  );
}
