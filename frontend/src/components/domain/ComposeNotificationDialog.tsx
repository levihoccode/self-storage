import { Send } from "lucide-react";
import { FormEvent, useId } from "react";
import { addNotification, notificationRecipients } from "../../mocks/notifications";
import { Button } from "../ui/Button";
import { FIELD_LABEL, FormField, SelectField } from "../ui/FormField";
import { useModalA11y } from "../ui/useModalA11y";

const TEXTAREA_CLASS =
  "min-h-[110px] w-full rounded-sm border border-border bg-surface-subtle px-[13px] py-[11px] text-[14px] text-ink outline-0 placeholder:text-[#7d8882] focus:border-brand focus:shadow-[0_0_0_3px_var(--brand-soft)]";

/**
 * Demo UI cho thông báo thủ công type `OTHER` (spec db-table-draft.md:
 * recipient do người tạo chọn; quyền tạo thật chờ Flow 5.0). Ghi thẳng vào
 * mock — thay bằng POST /api/notifications khi BE sẵn sàng.
 */
export function ComposeNotificationDialog({
  onClose,
  onCreated,
}: {
  onClose: () => void;
  onCreated: (recipientName: string) => void;
}) {
  const titleId = useId();
  const containerRef = useModalA11y<HTMLDivElement>(onClose);

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    const recipient = notificationRecipients.find(
      (candidate) => candidate.email === String(data.get("recipient") || ""),
    );
    const title = String(data.get("title") || "").trim();
    const content = String(data.get("content") || "").trim();
    if (!recipient || !title || !content) return;
    addNotification({ recipient, title, content });
    onCreated(recipient.name);
  }

  return (
    <div
      className="fixed inset-0 z-[90] grid place-items-center bg-[rgba(7,16,19,0.68)] p-5"
      role="presentation"
      onClick={onClose}
    >
      <div
        ref={containerRef}
        tabIndex={-1}
        className="w-[min(520px,100%)] rounded-md border border-border bg-surface p-7 shadow-soft-token focus:outline-none"
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        onClick={(event) => event.stopPropagation()}
      >
        <h2 id={titleId} className="mb-1 mt-0 text-[19px] leading-[1.2] tracking-[-0.03em]">
          Tạo thông báo
        </h2>
        <p className="mb-6 mt-0 text-[12px] leading-normal text-muted">
          Thông báo thủ công (type <code>OTHER</code>) gửi tới một khách hàng — demo UI, chưa gọi
          API.
        </p>
        <form onSubmit={submit}>
          <SelectField
            label="Người nhận"
            name="recipient"
            required
            options={notificationRecipients.map((recipient) => ({
              value: recipient.email,
              label: `${recipient.name} — ${recipient.email}`,
            }))}
          />
          <FormField
            label="Tiêu đề"
            name="title"
            required
            placeholder="Ví dụ: Kho Mộc tạm đóng cổng phụ ngày 12/10"
          />
          <div className="mb-[17px]">
            <label className={FIELD_LABEL} htmlFor="content">
              Nội dung
              <span className="text-danger" aria-hidden="true">
                {" *"}
              </span>
            </label>
            <textarea
              id="content"
              name="content"
              required
              className={TEXTAREA_CLASS}
              placeholder="Nêu rõ sự việc và mốc thời gian liên quan."
            />
          </div>
          <div className="flex justify-end gap-2.5">
            <Button variant="secondary" onClick={onClose}>
              Hủy
            </Button>
            <Button type="submit">
              <Send size={16} /> Gửi thông báo
            </Button>
          </div>
        </form>
      </div>
    </div>
  );
}
