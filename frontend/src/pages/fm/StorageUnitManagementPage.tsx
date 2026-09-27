import { AlertTriangle, ArrowRight, Check, Plus, RotateCcw } from "lucide-react";
import { FormEvent, useState } from "react";
import type { Navigate } from "../../app/types";
import { unitTypes } from "../../mocks/catalog";
import {
  createFmUnit,
  fmUnits,
  setFmUnitStatus,
  type FmUnit,
  type FmUnitStatus,
} from "../../mocks/fm";
import { DemoNotice } from "../../components/ui/DemoNotice";
import { DetailPanel } from "../../components/ui/DetailPanel";
import { FormField, SelectField } from "../../components/ui/FormField";
import { SurfaceState } from "../../components/ui/SurfaceState";

type StatusFilter = "all" | FmUnitStatus;

const STATUS_LABEL: Record<FmUnitStatus, string> = {
  Available: "Trống",
  OnHold: "Đang giữ",
  Reserved: "Đã đặt trước",
  Rented: "Đang cho thuê",
  Maintenance: "Bảo trì",
};

const STATUS_BADGE: Record<FmUnitStatus, string> = {
  Available: "bg-success/14 text-success",
  OnHold: "bg-warning/14 text-warning",
  Reserved: "bg-warning/14 text-warning",
  Rented: "bg-info/14 text-info",
  Maintenance: "bg-surface-subtle text-muted",
};

const PRIMARY_BUTTON =
  "inline-flex min-h-11 items-center justify-center gap-2.5 rounded-sm border border-transparent bg-brand px-[18px] text-[14px] font-[750] text-background transition-colors duration-[180ms] ease hover:bg-brand-strong disabled:cursor-not-allowed disabled:opacity-60";
const SECONDARY_BUTTON =
  "inline-flex min-h-11 items-center justify-center gap-2.5 rounded-sm border border-brand bg-transparent px-[18px] text-[14px] font-[750] text-brand transition-colors duration-[180ms] ease hover:bg-brand hover:text-background disabled:cursor-not-allowed disabled:opacity-60";
const FILTER_SELECT =
  "min-w-[170px] rounded-sm border border-border bg-surface px-[11px] py-[10px] pr-[30px] text-[12px] font-semibold text-ink";

function unitTypeName(unitTypeId: string) {
  return unitTypes.find((type) => type.id === unitTypeId)?.name ?? unitTypeId;
}

export function StorageUnitManagementPage({ navigate }: { navigate: Navigate }) {
  const [, setRefresh] = useState(0);
  const [statusFilter, setStatusFilter] = useState<StatusFilter>("all");
  const [typeFilter, setTypeFilter] = useState<string>("all");
  const [openId, setOpenId] = useState<string | null>(null);
  const [creating, setCreating] = useState(false);
  const [maintenanceReason, setMaintenanceReason] = useState("");
  const [mode, setMode] = useState<"view" | "maintenance">("view");

  const list = fmUnits.filter(
    (unit) =>
      (statusFilter === "all" || unit.status === statusFilter) &&
      (typeFilter === "all" || unit.unitTypeId === typeFilter),
  );
  const openUnit = fmUnits.find((unit) => unit.id === openId) ?? null;

  function openDetails(unit: FmUnit) {
    setOpenId(unit.id);
    setMode("view");
    setMaintenanceReason("");
  }

  function submitCreate(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    const code = String(data.get("unitCode") ?? "").trim();
    const unitTypeId = String(data.get("unitTypeId") ?? "");
    const location = String(data.get("location") ?? "").trim();
    if (!code || !unitTypeId || !location) return;
    createFmUnit({ code, unitTypeId, location });
    setRefresh((count) => count + 1);
    setCreating(false);
    event.currentTarget.reset();
  }

  function confirmMaintenance(unit: FmUnit) {
    if (!maintenanceReason.trim()) return;
    setFmUnitStatus(unit.id, "Maintenance");
    setRefresh((count) => count + 1);
    setOpenId(null);
  }

  function reopenAvailable(unit: FmUnit) {
    setFmUnitStatus(unit.id, "Available");
    setRefresh((count) => count + 1);
    setOpenId(null);
  }

  return (
    <main>
      <div className="mb-8 flex flex-wrap items-start justify-between gap-4">
        <div>
          <h1 className="m-0 text-[28px] tracking-[-0.03em] text-ink">Quản lý khoang chứa</h1>
          <p className="mt-2 text-[13px] text-muted">
            Danh sách khoang vật lý tại cơ sở của bạn — giá thuê do BOM quản lý theo loại kho, không
            chỉnh ở đây.
          </p>
        </div>
        <button className={PRIMARY_BUTTON} onClick={() => setCreating((value) => !value)}>
          <Plus size={16} /> Tạo khoang mới
        </button>
      </div>

      {creating && (
        <form onSubmit={submitCreate} className="mb-8 border border-border bg-surface p-6">
          <h2 className="m-0 mb-4 text-[15px] font-bold text-ink">Khoang mới</h2>
          <div className="grid grid-cols-3 gap-4 max-[760px]:grid-cols-1">
            <FormField label="Mã khoang" name="unitCode" placeholder="VD: TD-108" required />
            <SelectField
              label="Loại kho"
              name="unitTypeId"
              required
              options={unitTypes.map((type) => ({ value: type.id, label: type.name }))}
            />
            <FormField label="Vị trí" name="location" placeholder="VD: Tầng 2 · Dãy C" required />
          </div>
          <div className="mt-2 flex items-center gap-3">
            <button type="submit" className={PRIMARY_BUTTON}>
              <Check size={16} /> Lưu khoang
            </button>
            <button
              type="button"
              className="border-0 bg-transparent px-3 py-2 text-[12px] text-muted hover:text-brand"
              onClick={() => setCreating(false)}
            >
              Huỷ
            </button>
          </div>
        </form>
      )}

      <div className="mb-6 flex flex-wrap items-center gap-3">
        <select
          className={FILTER_SELECT}
          value={statusFilter}
          onChange={(event) => setStatusFilter(event.target.value as StatusFilter)}
        >
          <option value="all">Tất cả trạng thái</option>
          <option value="Available">Trống</option>
          <option value="OnHold">Đang giữ</option>
          <option value="Reserved">Đã đặt trước</option>
          <option value="Rented">Đang cho thuê</option>
          <option value="Maintenance">Bảo trì</option>
        </select>
        <select
          className={FILTER_SELECT}
          value={typeFilter}
          onChange={(event) => setTypeFilter(event.target.value)}
        >
          <option value="all">Tất cả loại kho</option>
          {unitTypes.map((type) => (
            <option key={type.id} value={type.id}>
              {type.name}
            </option>
          ))}
        </select>
      </div>

      {list.length === 0 ? (
        <SurfaceState variant="empty" title="Không có khoang nào phù hợp bộ lọc" />
      ) : (
        <div className="grid gap-3">
          {list.map((unit) => (
            <button
              key={unit.id}
              className="flex items-start justify-between gap-5 border border-border bg-surface p-5 text-left transition-colors hover:border-brand max-[760px]:flex-col"
              onClick={() => openDetails(unit)}
            >
              <div>
                <p className="m-0 text-[15px] font-bold text-ink">{unit.code}</p>
                <p className="mb-0 mt-1.5 text-[13px] text-muted">
                  {unitTypeName(unit.unitTypeId)} · {unit.size} · {unit.location}
                </p>
              </div>
              <span
                className={`whitespace-nowrap rounded-full px-[9px] py-[6px] text-[11px] font-extrabold ${STATUS_BADGE[unit.status]}`}
              >
                {STATUS_LABEL[unit.status]}
              </span>
            </button>
          ))}
        </div>
      )}

      {openUnit && (
        <DetailPanel
          title={openUnit.code}
          eyebrow={`${unitTypeName(openUnit.unitTypeId)} · ${openUnit.size}`}
          onClose={() => setOpenId(null)}
        >
          <div className="grid gap-2.5 text-[13px] text-muted">
            <span>Vị trí: {openUnit.location}</span>
            <span>
              Trạng thái hiện tại:{" "}
              <span
                className={`rounded-full px-2 py-0.5 text-[11px] font-bold ${STATUS_BADGE[openUnit.status]}`}
              >
                {STATUS_LABEL[openUnit.status]}
              </span>
            </span>
          </div>

          {openUnit.status === "Available" && mode === "view" && (
            <div className="mt-6">
              <button className={SECONDARY_BUTTON} onClick={() => setMode("maintenance")}>
                <AlertTriangle size={16} /> Chuyển sang Bảo trì
              </button>
            </div>
          )}

          {openUnit.status === "Available" && mode === "maintenance" && (
            <div className="mt-6 border border-border bg-surface-subtle p-4">
              <label className="grid gap-1.5 text-[12px] font-bold text-ink">
                Lý do chuyển bảo trì (bắt buộc, sẽ lưu vào nhật ký thao tác)
                <textarea
                  className="min-h-[80px] rounded-sm border border-border bg-surface p-2.5 text-[13px] font-normal text-ink"
                  value={maintenanceReason}
                  onChange={(event) => setMaintenanceReason(event.target.value)}
                  placeholder="VD: Phát hiện ẩm mốc, cần xử lý trước khi cho thuê tiếp…"
                />
              </label>
              <div className="mt-3 flex items-center gap-2.5">
                <button
                  className={PRIMARY_BUTTON}
                  disabled={!maintenanceReason.trim()}
                  onClick={() => confirmMaintenance(openUnit)}
                >
                  <AlertTriangle size={16} /> Xác nhận chuyển bảo trì
                </button>
                <button
                  className="border-0 bg-transparent px-3 py-2 text-[12px] text-muted hover:text-brand"
                  onClick={() => setMode("view")}
                >
                  Quay lại
                </button>
              </div>
            </div>
          )}

          {openUnit.status === "Maintenance" && (
            <div className="mt-6">
              <button className={PRIMARY_BUTTON} onClick={() => reopenAvailable(openUnit)}>
                <RotateCcw size={16} /> Chuyển về Trống
              </button>
            </div>
          )}

          {(openUnit.status === "OnHold" || openUnit.status === "Reserved") && (
            <div className="mt-6">
              <DemoNotice tone="pending">
                Khoang đang gắn với 1 yêu cầu/đơn hàng đang xử lý — cần giải quyết yêu cầu/đơn đó
                trước, không đổi trạng thái trực tiếp ở đây.
              </DemoNotice>
            </div>
          )}

          {openUnit.status === "Rented" && (
            <div className="mt-6">
              <DemoNotice tone="pending">
                Khoang đang có khách thuê — không tự ý chuyển bảo trì. Nếu có sự cố, hãy tạo yêu cầu
                hỗ trợ để FS xử lý.
              </DemoNotice>
              <button
                className={`${SECONDARY_BUTTON} mt-4`}
                onClick={() => navigate("/fm/support-requests")}
              >
                Tới hàng chờ yêu cầu hỗ trợ <ArrowRight size={14} />
              </button>
            </div>
          )}
        </DetailPanel>
      )}
    </main>
  );
}
