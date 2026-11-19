import { Archive, Check, Gavel, PackageOpen } from "lucide-react";
import { useState } from "react";
import { formatDate, formatDateTime, formatMoney } from "../../app/format";
import { fetchFacilityStaff, type FmStaff } from "../../app/fmStaff";
import {
  fetchReclaimBoard,
  liquidateReclaimedGoods,
  loadErrorState,
  markGoodsSurplusRefunded,
  reclaimContract,
  returnReclaimedGoods,
  useFmAction,
  useFmLoad,
  type ReclaimCandidate,
  type ReclaimedGoods,
} from "../../app/services/fm";
import {
  FileUploadField,
  INPUT_CLASS,
  TEXTAREA_CLASS,
  attachUploaded,
  type PickedFile,
} from "../../components/domain/FileUploadField";
import { Button } from "../../components/ui/Button";
import { DemoNotice } from "../../components/ui/DemoNotice";
import { FIELD_LABEL } from "../../components/ui/FormField";
import { StatusTag } from "../../components/ui/StatusTag";
import { SurfaceState } from "../../components/ui/SurfaceState";

const GOODS_TAG: Record<
  string,
  { label: string; tone: "success" | "warning" | "danger" | "info" | "neutral" }
> = {
  Holding: { label: "Đang lưu giữ", tone: "warning" },
  Returned: { label: "Đã trả khách", tone: "success" },
  Liquidated: { label: "Đã thanh lý", tone: "neutral" },
};

function staffLabel(staff: FmStaff) {
  return staff.name ? `${staff.name} · ${staff.email}` : staff.email;
}

/**
 * Route `/fm/reclaims`: hợp đồng quá hạn kéo dài khách bỏ đi — thu hồi khoang (FS chứng kiến, biên bản kiểm kê,
 * ảnh) → lưu giữ đồ → trả đồ khi khách trả hết nợ + phí lưu giữ, hoặc thanh lý sau hạn lưu giữ.
 */
export function ReclaimPage() {
  const board = useFmLoad("reclaims", (facility) => fetchReclaimBoard(facility.id));
  const staff = useFmLoad("staff", (facility) => fetchFacilityStaff(facility.id));
  const [notice, setNotice] = useState<string | null>(null);
  const candidates = board.data?.candidates ?? [];
  const goods = board.data?.goods ?? [];
  const holding = goods.filter((item) => item.status === "Holding");
  const closed = goods.filter((item) => item.status !== "Holding");

  function done(message: string) {
    setNotice(message);
    board.reload();
  }

  return (
    <div className="grid gap-6">
      <div>
        <h1 className="m-0 text-title text-ink">Thu hồi & lưu giữ</h1>
        <p className="mt-1.5 text-small text-muted">
          Hợp đồng quá hạn kéo dài đang tạm khoá mà khách không xử lý: tới mốc thì thu hồi khoang
          (có FS chứng kiến, biên bản kiểm kê và ảnh), đồ chuyển về khu lưu giữ. Khách trả hết công
          nợ + phí lưu giữ thì trả đồ; hết hạn lưu giữ thì thanh lý — tiền thanh lý trừ nợ, dư hoàn
          khách.
        </p>
      </div>

      {notice && <DemoNotice tone="success">{notice}</DemoNotice>}
      {staff.error && (
        <DemoNotice tone="error">Không tải được danh sách FS: {staff.error.message}</DemoNotice>
      )}

      {board.error ? (
        <SurfaceState
          {...loadErrorState(board.error, "thu hồi & lưu giữ")}
          action={{ label: "Thử lại", onClick: board.reload }}
        />
      ) : !board.data ? (
        <SurfaceState variant="loading" title="Đang tải…" />
      ) : (
        <>
          <section className="grid gap-2">
            <h2 className="m-0 flex items-center gap-2 text-section text-ink">
              <PackageOpen size={16} /> Chờ thu hồi
            </h2>
            {candidates.length === 0 ? (
              <p className="m-0 bg-surface p-4 text-small text-muted">
                Không có hợp đồng nào tới mốc thu hồi.
              </p>
            ) : (
              candidates.map((item) => (
                <CandidateRow
                  key={item.contractId}
                  item={item}
                  staff={staff.data ?? []}
                  onDone={(result) =>
                    done(
                      `Đã thu hồi khoang ${result.unitCode} — đồ lưu giữ tới ${formatDate(result.holdUntil)}.`,
                    )
                  }
                />
              ))
            )}
          </section>

          <section className="grid gap-2">
            <h2 className="m-0 flex items-center gap-2 text-section text-ink">
              <Archive size={16} /> Đồ đang lưu giữ
            </h2>
            {holding.length === 0 ? (
              <p className="m-0 bg-surface p-4 text-small text-muted">Không có đồ đang lưu giữ.</p>
            ) : (
              holding.map((item) => <GoodsRow key={item.id} item={item} onDone={done} />)
            )}
          </section>

          {closed.length > 0 && (
            <section className="grid gap-2">
              <h2 className="m-0 text-section text-ink">Đã xử lý</h2>
              {closed.map((item) => (
                <GoodsRow key={item.id} item={item} onDone={done} />
              ))}
            </section>
          )}
        </>
      )}
    </div>
  );
}

function CandidateRow({
  item,
  staff,
  onDone,
}: {
  item: ReclaimCandidate;
  staff: FmStaff[];
  onDone: (result: ReclaimedGoods) => void;
}) {
  const [open, setOpen] = useState(false);
  const [witness, setWitness] = useState("");
  const [note, setNote] = useState("");
  const [location, setLocation] = useState("");
  const [photos, setPhotos] = useState<PickedFile[]>([]);
  const action = useFmAction();
  const ready = !!witness && note.trim() !== "" && location.trim() !== "" && photos.length > 0;

  async function submit() {
    const outcome = await action.run("reclaim", () =>
      reclaimContract(item.contractId, {
        witnessStaffId: Number(witness),
        inventoryNote: note.trim(),
        photos: photos.map((photo) => photo.url),
        storageLocation: location.trim(),
      }),
    );
    if (outcome.ok) {
      await attachUploaded(photos);
      onDone(outcome.value);
    }
  }

  return (
    <article className="grid gap-3 bg-surface p-4">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0">
          <strong className="block text-small font-semibold text-ink">
            {item.contractCode} · Khoang {item.unitCode}
          </strong>
          <span className="mt-0.5 block text-label text-muted">
            Hết hạn {formatDate(item.endDate)} · quá hạn {item.daysOverdue} ngày · mốc thu hồi{" "}
            {formatDate(item.reclaimDate)}
          </span>
          <span className="mt-0.5 block text-label text-muted">
            Công nợ hiện tại {formatMoney(item.outstanding)} · tiền cọc{" "}
            {formatMoney(item.depositAmount)}
          </span>
        </div>
        {item.eligible ? (
          <Button size="sm" variant={open ? "secondary" : "primary"} onClick={() => setOpen(!open)}>
            {open ? "Đóng" : "Thu hồi khoang"}
          </Button>
        ) : (
          <StatusTag tone={item.returnRequestOpen ? "info" : "warning"}>
            {item.returnRequestOpen
              ? "Khách đang có yêu cầu trả kho"
              : `Thu hồi được từ ${formatDate(item.reclaimDate)}`}
          </StatusTag>
        )}
      </div>
      {open && item.eligible && (
        <div className="grid gap-3 border-t border-rule pt-3">
          <p className="m-0 text-small text-muted">
            Thu hồi = kết thúc hợp đồng, chìa cơ ghi mất (khoang vào bảo trì để thay khoá), công nợ
            + phí thu hồi + phí mất chìa trừ vào cọc, phần thiếu thành công nợ của khách.
          </p>
          <label className="grid gap-1.5">
            <span className={FIELD_LABEL}>FS chứng kiến</span>
            <select
              className={INPUT_CLASS}
              value={witness}
              onChange={(event) => setWitness(event.target.value)}
            >
              <option value="">— Chọn FS —</option>
              {staff.map((member) => (
                <option key={member.id} value={member.id}>
                  {staffLabel(member)}
                </option>
              ))}
            </select>
          </label>
          <label className="grid gap-1.5">
            <span className={FIELD_LABEL}>Biên bản kiểm kê (từng món, tình trạng)</span>
            <textarea
              className={TEXTAREA_CLASS}
              maxLength={4000}
              value={note}
              onChange={(event) => setNote(event.target.value)}
              placeholder="Vd: 3 thùng carton dán băng keo, 1 xe đạp màu xanh, 1 tủ nhựa 3 ngăn…"
            />
          </label>
          <label className="grid gap-1.5">
            <span className={FIELD_LABEL}>Vị trí lưu giữ</span>
            <input
              className={INPUT_CLASS}
              maxLength={200}
              value={location}
              onChange={(event) => setLocation(event.target.value)}
              placeholder="Vd: Khu lưu giữ tầng 1 — kệ A"
            />
          </label>
          <FileUploadField
            label="Ảnh khoang và đồ khi mở (bắt buộc, tối đa 20)"
            files={photos}
            onChange={setPhotos}
          />
          <div>
            <Button pending={action.pending === "reclaim"} disabled={!ready} onClick={submit}>
              {action.pending !== "reclaim" && <Check size={14} />} Xác nhận thu hồi khoang
            </Button>
          </div>
        </div>
      )}
      {action.error && <DemoNotice tone="error">{action.error}</DemoNotice>}
    </article>
  );
}

function GoodsRow({ item, onDone }: { item: ReclaimedGoods; onDone: (message: string) => void }) {
  const [liquidating, setLiquidating] = useState(false);
  const [proceeds, setProceeds] = useState("");
  const [note, setNote] = useState("");
  const [photos, setPhotos] = useState<PickedFile[]>([]);
  const action = useFmAction();
  const tag = GOODS_TAG[item.status] ?? { label: item.status, tone: "neutral" as const };
  const surplusPending = Number(item.surplusAmount ?? 0) > 0 && !item.surplusRefundedAt;

  async function confirmReturn() {
    const outcome = await action.run("return", () => returnReclaimedGoods(item.id));
    if (outcome.ok) onDone(`Đã trả đồ khoang ${item.unitCode} cho khách.`);
  }

  async function liquidate() {
    const outcome = await action.run("liquidate", () =>
      liquidateReclaimedGoods(item.id, {
        proceeds: Number(proceeds),
        note: note.trim(),
        photos: photos.map((photo) => photo.url),
      }),
    );
    if (outcome.ok) {
      await attachUploaded(photos);
      const surplus = Number(outcome.value.surplusAmount ?? 0);
      onDone(
        `Đã thanh lý đồ khoang ${item.unitCode}.${
          surplus > 0 ? ` Còn dư ${formatMoney(surplus)} — cần hoàn cho khách.` : ""
        }`,
      );
    }
  }

  async function refunded() {
    const outcome = await action.run("refund", () => markGoodsSurplusRefunded(item.id));
    if (outcome.ok) onDone(`Đã ghi nhận hoàn tiền dư cho khách (khoang ${item.unitCode}).`);
  }

  return (
    <article className="grid gap-3 bg-surface p-4">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0">
          <strong className="block text-small font-semibold text-ink">
            {item.contractCode} · Khoang {item.unitCode}
          </strong>
          <span className="mt-0.5 block text-label text-muted">
            Thu hồi {formatDateTime(item.reclaimedAt)}
            {item.witnessName ? ` · FS chứng kiến ${item.witnessName}` : ""} ·{" "}
            {item.storageLocation}
          </span>
          <span className="mt-0.5 block text-label text-muted">
            {item.status === "Holding"
              ? `Lưu giữ tới ${formatDate(item.holdUntil)} · đã giữ ${item.daysHeld} ngày`
              : item.status === "Returned"
                ? `Đã trả khách ${formatDateTime(item.returnedAt)}`
                : `Thanh lý ${formatDateTime(item.liquidatedAt)} · thu ${formatMoney(item.proceedsAmount ?? 0)}`}
            {" · "}công nợ chưa trả {formatMoney(item.outstandingDebt)}
          </span>
          <p className="m-0 mt-1 whitespace-pre-line text-small text-ink">{item.inventoryNote}</p>
        </div>
        <StatusTag tone={tag.tone}>{tag.label}</StatusTag>
      </div>

      {item.status === "Holding" && (
        <div className="flex flex-wrap items-center gap-2">
          {item.returnRequestedAt ? (
            <>
              <span className="text-small text-ink">
                Khách yêu cầu nhận lại {formatDateTime(item.returnRequestedAt)}
                {item.returnReady ? " — đã trả hết nợ." : " — chờ khách thanh toán."}
              </span>
              <Button
                size="sm"
                disabled={!item.returnReady}
                pending={action.pending === "return"}
                onClick={confirmReturn}
              >
                Đã trả đồ cho khách
              </Button>
            </>
          ) : (
            <span className="text-label text-muted">Khách chưa yêu cầu nhận lại đồ.</span>
          )}
          {item.liquidationDue && (
            <Button size="sm" variant="secondary" onClick={() => setLiquidating(!liquidating)}>
              <Gavel size={14} /> {liquidating ? "Đóng" : "Thanh lý"}
            </Button>
          )}
        </div>
      )}

      {liquidating && item.liquidationDue && (
        <div className="grid gap-3 border-t border-rule pt-3">
          <p className="m-0 text-small text-muted">
            Tiền thanh lý trừ công nợ + phí lưu giữ; dư thì hoàn cho khách, thiếu thì thành công nợ.
            Giấy tờ cá nhân không bán — gửi trả khách hoặc bàn giao cơ quan chức năng, ghi rõ trong
            biên bản.
          </p>
          <label className="grid gap-1.5">
            <span className={FIELD_LABEL}>Tổng tiền thanh lý (VND)</span>
            <input
              className={INPUT_CLASS}
              inputMode="numeric"
              value={proceeds}
              onChange={(event) => setProceeds(event.target.value.replace(/\D/g, ""))}
              placeholder="0"
            />
          </label>
          <label className="grid gap-1.5">
            <span className={FIELD_LABEL}>
              Biên bản thanh lý (bán / trả giấy tờ / bàn giao / huỷ)
            </span>
            <textarea
              className={TEXTAREA_CLASS}
              maxLength={4000}
              value={note}
              onChange={(event) => setNote(event.target.value)}
            />
          </label>
          <FileUploadField
            label="Ảnh / chứng từ thanh lý (bắt buộc, tối đa 20)"
            files={photos}
            onChange={setPhotos}
          />
          <div>
            <Button
              pending={action.pending === "liquidate"}
              disabled={proceeds === "" || note.trim() === "" || photos.length === 0}
              onClick={liquidate}
            >
              Xác nhận thanh lý
            </Button>
          </div>
        </div>
      )}

      {item.status === "Liquidated" && Number(item.surplusAmount ?? 0) > 0 && (
        <div className="flex flex-wrap items-center gap-2 text-small text-ink">
          Tiền dư {formatMoney(item.surplusAmount ?? 0)}{" "}
          {item.surplusRefundedAt
            ? `· đã hoàn ${formatDateTime(item.surplusRefundedAt)}`
            : "· chưa hoàn cho khách"}
          {surplusPending && (
            <Button size="sm" pending={action.pending === "refund"} onClick={refunded}>
              Đã hoàn tiền dư
            </Button>
          )}
        </div>
      )}
      {action.error && <DemoNotice tone="error">{action.error}</DemoNotice>}
    </article>
  );
}
