import { FormEvent, useState } from "react";
import { ArrowLeft, ArrowRight, LockKeyhole } from "lucide-react";
import { Navigate } from "../app/types";
import { PAGE_CONTAINER } from "../app/layout";
import { facilities, unitTypes } from "../mocks/catalog";
import { FormField, SelectField } from "../components/ui/FormField";
import { SuccessState } from "../components/ui/SuccessState";

const PRIMARY_BUTTON =
  "inline-flex min-h-11 items-center justify-center gap-2.5 rounded-sm border border-transparent bg-brand px-[18px] py-0 text-[14px] font-[750] text-background transition-colors duration-[180ms] ease hover:bg-brand-strong";
const FORM_PAGE = "min-h-[calc(100vh-76px)] bg-background pb-[100px] pt-[76px]";

export function RequestPage({ navigate }: { navigate: Navigate }) {
  const [submitted, setSubmitted] = useState(false);
  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSubmitted(true);
  }
  if (submitted)
    return (
      <section className={FORM_PAGE}>
        <div className="mx-auto w-[min(620px,calc(100%-40px))]">
          <SuccessState
            title="Đã nhận nhu cầu."
            description="Đội ngũ vận hành sẽ kiểm tra phương án và phản hồi qua email hoặc số điện thoại."
            action={
              <button className={`mt-6 ${PRIMARY_BUTTON}`} onClick={() => navigate("/units")}>
                Xem lại phương án kho <ArrowRight size={16} />
              </button>
            }
          />
        </div>
      </section>
    );
  return (
    <section className={FORM_PAGE}>
      <div
        className={`${PAGE_CONTAINER} grid grid-cols-[0.8fr_1.2fr] items-start gap-[76px] max-[760px]:grid-cols-1`}
      >
        <div className="pt-[5px]">
          <button
            className="mb-[58px] inline-flex items-center gap-2 border-0 bg-transparent p-0 text-[12px] font-bold text-muted hover:text-brand max-[760px]:mb-11"
            onClick={() => navigate("/units")}
          >
            <ArrowLeft size={16} /> Quay lại phương án kho
          </button>
          <h1 className="m-0 text-[clamp(40px,5vw,58px)] font-bold leading-[1.03] tracking-[-0.045em] text-ink">
            Gửi nhu cầu lưu trữ.
          </h1>
          <p className="m-0 mb-[30px] mt-[23px] max-w-[360px] text-[13px] leading-[1.65] text-muted">
            Chọn điểm kho, quy mô và thời gian bắt đầu.
          </p>
        </div>
        <form onSubmit={submit}>
          <div className="rounded-md border border-border bg-surface p-7">
            <div className="flex items-start gap-[14px] border-b border-border pb-[22px]">
              <div>
                <h2 className="m-0 text-[20px] font-bold tracking-[-0.05em] text-ink">
                  Thông tin lưu trữ
                </h2>
                <p className="m-0 mt-[3px] text-[11px] text-muted">
                  Các trường có dấu * là bắt buộc.
                </p>
              </div>
            </div>
            <div className="grid grid-cols-2 gap-x-[14px] pt-6 max-[760px]:grid-cols-1">
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
            <div className="mt-2 flex items-center justify-between gap-4 border-t border-border pt-5 max-[760px]:flex-col max-[760px]:items-stretch">
              <span className="flex items-center gap-[6px] text-[10px] text-muted">
                <LockKeyhole size={14} /> Chưa cần thanh toán ở bước này
              </span>
              <button className={`${PRIMARY_BUTTON} max-[760px]:w-full`} type="submit">
                Gửi nhu cầu <ArrowRight size={16} />
              </button>
            </div>
          </div>
        </form>
      </div>
    </section>
  );
}
