import {
  AlertTriangle,
  ArrowLeft,
  ArrowRight,
  Download,
  FileWarning,
  MapPin,
  X,
} from "lucide-react";
import { useState } from "react";
import type { Navigate } from "../app/types";
import {
  type Contract,
  contracts,
  type ExtendRequest,
  type ReturnRequest,
  SUPPORT_ISSUE_LABEL,
  type SupportIssueType,
  type SupportRequest,
} from "../mocks/contracts";
import { formatPrice } from "../mocks/catalog";
import { invoices } from "../mocks/invoices";
import { Button, buttonClassName } from "../components/ui/Button";
import { DemoNotice } from "../components/ui/DemoNotice";
import { DetailPanel } from "../components/ui/DetailPanel";
import { SurfaceState } from "../components/ui/SurfaceState";

const SECTION_TITLE = "m-0 text-[15px] font-bold text-ink";

const SUPPORT_STATUS_LABEL: Record<SupportRequest["status"], string> = {
  Open: "Mới gửi",
  Assigned: "Đã phân công",
  InProgress: "Đang xử lý",
  Resolved: "Đã xử lý",
  Closed: "Đã đóng",
};

function getContractId() {
  const segments = window.location.pathname.split("/").filter(Boolean);
  return segments[segments.length - 1] ?? "";
}

export function ContractDetailPage({ navigate }: { navigate: Navigate }) {
  const id = getContractId();
  const initial = contracts[id];

  const [contract, setContract] = useState<Contract | undefined>(initial);
  const [activePanel, setActivePanel] = useState<"extend" | "return" | "support" | null>(null);

  if (!contract) {
    return (
      <main>
        <SurfaceState
          variant="not-found"
          title="Không tìm thấy hợp đồng"
          description="Đường dẫn không đúng hoặc hợp đồng không thuộc tài khoản của bạn."
          action={{ label: "Về Kho của tôi", onClick: () => navigate("/my-storage") }}
        />
      </main>
    );
  }

  const contractInvoices = invoices.filter((invoice) => invoice.unitCode === contract.unitCode);

  function updateContract(patch: Partial<Contract>) {
    setContract((current) => (current ? { ...current, ...patch } : current));
  }

  return (
    <main>
      <button
        className="mb-6 inline-flex items-center gap-2 border-0 bg-transparent p-0 text-[12px] font-bold text-muted hover:text-brand"
        onClick={() => navigate("/my-storage")}
      >
        <ArrowLeft size={16} /> Về Kho của tôi
      </button>

      <div className="mb-6 flex items-start justify-between gap-5 max-[760px]:flex-col">
        <div>
          <p className="m-0 font-mono text-[11px] font-bold uppercase tracking-[0.08em] text-brand">
            Khoang {contract.unitCode} · {contract.unitType}
          </p>
          <h1 className="mb-0 mt-1.5 text-[26px] tracking-[-0.03em] text-ink">
            {contract.facility}
          </h1>
          <p className="mb-0 mt-1.5 flex items-center gap-[6px] text-[13px] text-muted">
            <MapPin size={15} /> {contract.district} · {contract.size}
          </p>
        </div>
        {contract.pdfUrl && (
          <a href={contract.pdfUrl} className={buttonClassName("secondary")}>
            <Download size={16} /> Tải hợp đồng
          </a>
        )}
      </div>

      {contract.isOverdue && (
        <div className="mb-6">
          <DemoNotice tone="error">
            Hợp đồng đã quá hạn. Không thể gia hạn qua web — vui lòng liên hệ trực tiếp quản lý cơ
            sở (FM).
          </DemoNotice>
        </div>
      )}
      {!contract.isOverdue && contract.isExpiringSoon && (
        <div className="mb-6">
          <DemoNotice tone="pending">
            Hợp đồng sắp hết hạn ({contract.endDate}). Gửi yêu cầu gia hạn sớm để không bị gián
            đoạn.
          </DemoNotice>
        </div>
      )}

      <div className="grid grid-cols-[1.4fr_1fr] gap-6 max-[980px]:grid-cols-1">
        <div className="grid gap-6">
          <section className="border border-border bg-surface p-6">
            <h2 className={SECTION_TITLE}>Thông tin hợp đồng</h2>
            <dl className="mt-4 grid grid-cols-2 gap-4 max-[760px]:grid-cols-1">
              <Field label="Ngày ký" value={contract.signedAt} />
              <Field label="Hiệu lực" value={`${contract.startDate} — ${contract.endDate}`} />
              <Field label="Tiền cọc" value={`${formatPrice(contract.depositAmount)}đ`} />
              <Field label="Giá thuê / tháng" value={`${formatPrice(contract.monthlyPrice)}đ`} />
            </dl>
          </section>

          <section className="border border-border bg-surface p-6">
            <div className="flex items-center justify-between gap-4">
              <h2 className={SECTION_TITLE}>Hoá đơn</h2>
              <button
                className="border-0 bg-transparent p-0 text-[12px] font-bold text-brand hover:underline"
                onClick={() => navigate("/invoices")}
              >
                Xem tất cả
              </button>
            </div>
            {contractInvoices.length === 0 ? (
              <p className="m-0 mt-3 text-[13px] text-muted">Chưa có hoá đơn nào.</p>
            ) : (
              <div className="mt-3 grid gap-2.5">
                {contractInvoices.map((invoice) => (
                  <div
                    key={invoice.id}
                    className="flex items-center justify-between gap-4 border border-border bg-surface-subtle px-4 py-3 text-[12px]"
                  >
                    <span className="text-ink">{invoice.title}</span>
                    <strong>{formatPrice(invoice.amount)}đ</strong>
                  </div>
                ))}
              </div>
            )}
          </section>

          <section className="border border-border bg-surface p-6">
            <h2 className={SECTION_TITLE}>Lịch sử bàn giao</h2>
            <div className="mt-3 grid gap-3">
              {contract.handoverHistory.map((event) => (
                <div key={event.id} className="border-l-2 border-brand pl-3.5">
                  <p className="m-0 text-[12px] font-bold text-ink">
                    {event.label} · {event.date}
                  </p>
                  <p className="mb-0 mt-1 text-[12px] text-muted">{event.note}</p>
                </div>
              ))}
            </div>
          </section>

          <section className="border border-border bg-surface p-6">
            <h2 className={SECTION_TITLE}>Yêu cầu hỗ trợ</h2>
            {contract.supportRequests.length === 0 ? (
              <p className="m-0 mt-3 text-[13px] text-muted">Chưa có yêu cầu hỗ trợ nào.</p>
            ) : (
              <div className="mt-3 grid gap-2.5">
                {contract.supportRequests.map((request) => (
                  <div
                    key={request.id}
                    className="flex items-center justify-between gap-4 border border-border bg-surface-subtle px-4 py-3 text-[12px]"
                  >
                    <div>
                      <p className="m-0 font-bold text-ink">
                        {SUPPORT_ISSUE_LABEL[request.issueType]}
                      </p>
                      <p className="mb-0 mt-0.5 text-muted">{request.createdAt}</p>
                    </div>
                    <span className="rounded-full bg-brand-soft px-2.5 py-1 text-[11px] font-bold text-brand">
                      {SUPPORT_STATUS_LABEL[request.status]}
                    </span>
                  </div>
                ))}
              </div>
            )}
          </section>
        </div>

        <aside className="grid content-start gap-4">
          <h2 className={SECTION_TITLE}>Hành động</h2>

          {contract.openExtendRequest ? (
            <ExtendStatusCard
              request={contract.openExtendRequest}
              onCancel={() => updateContract({ openExtendRequest: null })}
              onPay={() => navigate("/invoices")}
            />
          ) : (
            !contract.openReturnRequest &&
            contract.availableActions.includes("extend") && (
              <Button onClick={() => setActivePanel("extend")}>Yêu cầu gia hạn</Button>
            )
          )}

          {contract.openReturnRequest ? (
            <ReturnStatusCard
              request={contract.openReturnRequest}
              onCancel={() => updateContract({ openReturnRequest: null })}
            />
          ) : (
            !contract.openExtendRequest &&
            contract.availableActions.includes("return") && (
              <Button variant="secondary" onClick={() => setActivePanel("return")}>
                Yêu cầu trả kho
              </Button>
            )
          )}

          {contract.availableActions.includes("report-issue") && (
            <button
              className="inline-flex min-h-11 items-center justify-center gap-2.5 rounded-sm border-0 bg-transparent px-[18px] text-[14px] font-[750] text-muted transition-colors hover:text-brand"
              onClick={() => setActivePanel("support")}
            >
              <FileWarning size={16} /> Báo sự cố
            </button>
          )}
        </aside>
      </div>

      {activePanel === "extend" && (
        <ExtendPanel
          contract={contract}
          onClose={() => setActivePanel(null)}
          onSubmit={(request) => {
            updateContract({ openExtendRequest: request });
            setActivePanel(null);
          }}
        />
      )}
      {activePanel === "return" && (
        <ReturnPanel
          onClose={() => setActivePanel(null)}
          onSubmit={(request) => {
            updateContract({ openReturnRequest: request });
            setActivePanel(null);
          }}
        />
      )}
      {activePanel === "support" && (
        <SupportPanel
          onClose={() => setActivePanel(null)}
          onSubmit={(request) => {
            updateContract({ supportRequests: [request, ...contract.supportRequests] });
            setActivePanel(null);
          }}
        />
      )}
    </main>
  );
}

function Field({ label, value }: { label: string; value: string }) {
  return (
    <div className="grid gap-[3px]">
      <span className="font-mono text-[10px] font-medium uppercase text-muted">{label}</span>
      <strong className="text-[13px] text-ink">{value}</strong>
    </div>
  );
}

function ExtendStatusCard({
  request,
  onCancel,
  onPay,
}: {
  request: ExtendRequest;
  onCancel: () => void;
  onPay: () => void;
}) {
  if (request.status === "PendingApproval") {
    return (
      <div className="border border-border bg-surface p-4">
        <DemoNotice tone="pending">Đang chờ FM duyệt gia hạn.</DemoNotice>
        <Button variant="secondary" className="mt-3.5 w-full" onClick={onCancel}>
          <X size={16} /> Huỷ yêu cầu
        </Button>
      </div>
    );
  }
  if (request.status === "ApprovedPendingPayment") {
    return (
      <div className="border border-border bg-surface p-4">
        <p className="m-0 text-[12px] font-bold text-ink">Yêu cầu đã được duyệt</p>
        <p className="mb-0 mt-1.5 text-[13px] text-muted">
          Thêm {request.extraMonths} tháng · {formatPrice(request.amount ?? 0)}đ · hạn thanh toán{" "}
          {request.dueDate}
        </p>
        <p className="mb-0 mt-2 text-[11px] text-muted">
          Không thể huỷ qua web — liên hệ FM trực tiếp nếu cần huỷ.
        </p>
        <Button className="mt-3.5 w-full" onClick={onPay}>
          Thanh toán gia hạn <ArrowRight size={16} />
        </Button>
      </div>
    );
  }
  return null;
}

function ReturnStatusCard({ request, onCancel }: { request: ReturnRequest; onCancel: () => void }) {
  if (request.status === "Assigned") {
    return (
      <div className="border border-border bg-surface p-4">
        <DemoNotice tone="success">
          Đã phân công nhân viên, ngày hẹn dự kiến {request.preferredDate}.
        </DemoNotice>
        <p className="mb-0 mt-2 text-[11px] text-muted">
          Không thể huỷ qua web — liên hệ FM/FS trực tiếp nếu cần đổi.
        </p>
      </div>
    );
  }
  return (
    <div className="border border-border bg-surface p-4">
      <DemoNotice tone="pending">Đang chờ FM phân công nhân viên xử lý.</DemoNotice>
      <Button variant="secondary" className="mt-3.5 w-full" onClick={onCancel}>
        <X size={16} /> Huỷ yêu cầu
      </Button>
    </div>
  );
}

function ExtendPanel({
  contract,
  onClose,
  onSubmit,
}: {
  contract: Contract;
  onClose: () => void;
  onSubmit: (request: ExtendRequest) => void;
}) {
  const [months, setMonths] = useState(1);

  return (
    <DetailPanel title="Yêu cầu gia hạn hợp đồng" onClose={onClose}>
      <p className="m-0 text-[13px] text-muted">
        Chọn số tháng muốn gia hạn thêm cho khoang {contract.unitCode}. Đội ngũ vận hành sẽ duyệt
        trước khi bạn thanh toán.
      </p>
      <label className="mt-5 grid gap-1.5 text-[12px] font-bold text-ink">
        Số tháng gia hạn
        <input
          type="number"
          min={1}
          max={12}
          value={months}
          onChange={(event) => setMonths(Number(event.target.value) || 1)}
          className="w-24 rounded-sm border border-border bg-surface-subtle px-3 py-2.5 text-[13px] text-ink"
        />
      </label>
      <p className="mb-0 mt-3 text-[12px] text-muted">
        Ước tính: {formatPrice(months * contract.monthlyPrice)}đ (chốt chính thức sau khi FM duyệt).
      </p>
      <Button
        className="mt-6 w-full"
        onClick={() => onSubmit({ status: "PendingApproval", extraMonths: months })}
      >
        Gửi yêu cầu gia hạn
      </Button>
    </DetailPanel>
  );
}

function ReturnPanel({
  onClose,
  onSubmit,
}: {
  onClose: () => void;
  onSubmit: (request: ReturnRequest) => void;
}) {
  const [date, setDate] = useState("");
  const [reason, setReason] = useState("");

  return (
    <DetailPanel title="Yêu cầu trả kho" onClose={onClose}>
      <label className="grid gap-1.5 text-[12px] font-bold text-ink">
        Ngày mong muốn trả kho
        <input
          type="date"
          min={new Date().toISOString().slice(0, 10)}
          value={date}
          onChange={(event) => setDate(event.target.value)}
          className="w-fit rounded-sm border border-border bg-surface-subtle px-3 py-2.5 text-[13px] text-ink"
        />
      </label>
      <label className="mt-5 grid gap-1.5 text-[12px] font-bold text-ink">
        Lý do (không bắt buộc)
        <textarea
          className="min-h-[70px] rounded-sm border border-border bg-surface-subtle p-2.5 text-[13px] font-normal text-ink"
          value={reason}
          onChange={(event) => setReason(event.target.value)}
        />
      </label>
      <Button
        className="mt-6 w-full"
        disabled={!date}
        onClick={() =>
          onSubmit({ status: "Pending", preferredDate: date, reason: reason || undefined })
        }
      >
        Gửi yêu cầu trả kho
      </Button>
    </DetailPanel>
  );
}

function SupportPanel({
  onClose,
  onSubmit,
}: {
  onClose: () => void;
  onSubmit: (request: SupportRequest) => void;
}) {
  const [issueType, setIssueType] = useState<SupportIssueType>("LostKey");
  const [description, setDescription] = useState("");

  return (
    <DetailPanel title="Báo sự cố" onClose={onClose}>
      <div className="mb-4">
        <DemoNotice tone="info">
          Bạn có thể gửi báo sự cố bất cứ lúc nào, kể cả khi hợp đồng quá hạn hoặc đang có yêu cầu
          khác chờ xử lý.
        </DemoNotice>
      </div>
      <label className="grid gap-1.5 text-[12px] font-bold text-ink">
        Loại sự cố
        <select
          className="rounded-sm border border-border bg-surface-subtle px-3 py-2.5 text-[13px] text-ink"
          value={issueType}
          onChange={(event) => setIssueType(event.target.value as SupportIssueType)}
        >
          {Object.entries(SUPPORT_ISSUE_LABEL).map(([value, label]) => (
            <option key={value} value={value}>
              {label}
            </option>
          ))}
        </select>
      </label>
      <label className="mt-5 grid gap-1.5 text-[12px] font-bold text-ink">
        Mô tả chi tiết
        <textarea
          className="min-h-[90px] rounded-sm border border-border bg-surface-subtle p-2.5 text-[13px] font-normal text-ink"
          value={description}
          onChange={(event) => setDescription(event.target.value)}
          placeholder="Mô tả sự cố để đội ngũ hỗ trợ xử lý nhanh hơn…"
        />
      </label>
      <Button
        className="mt-6 w-full"
        disabled={!description.trim()}
        onClick={() =>
          onSubmit({
            id: `support-${Date.now()}`,
            issueType,
            description,
            status: "Open",
            createdAt: new Date().toLocaleDateString("vi-VN"),
          })
        }
      >
        <AlertTriangle size={16} /> Gửi báo sự cố
      </Button>
    </DetailPanel>
  );
}
