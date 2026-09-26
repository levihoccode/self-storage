import { useEffect, useMemo, useState } from "react";
import { AlertTriangle, Percent, Plus, Search } from "lucide-react";
import type { Navigate } from "../../app/types";
import "./DiscountManagement.css";

type DiscountType = "Percent" | "Fixed";
type ApplyTo = "Deposit" | "Rental" | "Extension" | "All";
type DiscountStatus = "Active" | "Inactive";

type DiscountRecord = {
  id: string;
  code: string;
  name: string;
  discountType: DiscountType;
  value: number;
  applyTo: ApplyTo;
  minMonths: number | null;
  startAt: string;
  endAt: string;
  isActive: boolean;
};

const initialDiscounts: DiscountRecord[] = [
  {
    id: "disc-101",
    code: "Q4-NEW-USER",
    name: "Khách hàng mới Q4",
    discountType: "Percent",
    value: 10,
    applyTo: "Deposit",
    minMonths: 3,
    startAt: "2026-10-01",
    endAt: "2026-12-31",
    isActive: true,
  },
  {
    id: "disc-102",
    code: "LONG-TERM-500K",
    name: "Ưu đãi thuê dài hạn",
    discountType: "Fixed",
    value: 500000,
    applyTo: "Rental",
    minMonths: 6,
    startAt: "2026-09-01",
    endAt: "2026-11-30",
    isActive: true,
  },
  {
    id: "disc-103",
    code: "JULY-RESTOCK",
    name: "Giảm giá mùa đông",
    discountType: "Percent",
    value: 15,
    applyTo: "All",
    minMonths: null,
    startAt: "2026-07-01",
    endAt: "2026-07-31",
    isActive: false,
  },
];

const emptyDiscount: DiscountRecord = {
  id: "",
  code: "",
  name: "",
  discountType: "Percent",
  value: 0,
  applyTo: "All",
  minMonths: null,
  startAt: "",
  endAt: "",
  isActive: true,
};

export function DiscountManagement({ navigate }: { navigate: Navigate }) {
  const [discounts, setDiscounts] = useState<DiscountRecord[]>(initialDiscounts);
  const [selectedId, setSelectedId] = useState(initialDiscounts[0]?.id ?? "");
  const [query, setQuery] = useState("");
  const [draft, setDraft] = useState<DiscountRecord>(initialDiscounts[0] ?? emptyDiscount);

  const filteredDiscounts = useMemo(() => {
    const keyword = query.trim().toLowerCase();
    return discounts.filter((discount) => {
      const matchesSearch =
        !keyword ||
        discount.code.toLowerCase().includes(keyword) ||
        discount.name.toLowerCase().includes(keyword) ||
        discount.applyTo.toLowerCase().includes(keyword);
      return matchesSearch;
    });
  }, [discounts, query]);

  const selectedDiscount =
    filteredDiscounts.find((discount) => discount.id === selectedId) ?? filteredDiscounts[0] ?? null;

  useEffect(() => {
    if (selectedDiscount && selectedDiscount.id !== draft.id) {
      setDraft(selectedDiscount);
    }
  }, [selectedDiscount, draft.id]);

  function updateSelected(discount: DiscountRecord) {
    setSelectedId(discount.id);
    setDraft(discount);
  }

  function createNewDiscount() {
    const newDiscount: DiscountRecord = {
      ...emptyDiscount,
      id: `disc-${Date.now()}`,
      code: `NEW-${Date.now().toString().slice(-5)}`,
      name: "New Discount",
    };
    setDiscounts((current) => [newDiscount, ...current]);
    setSelectedId(newDiscount.id);
    setDraft(newDiscount);
  }

  const currentDiscount = draft;

  return (
    <main className="discount-page" style={{ position: "relative" }}>
      <section className="discount-hero">
        <div className="container discount-hero__inner">
          <div>
            <p className="eyebrow">Business Operation Manager</p>
            <h1>
              Discount <span>programs.</span>
            </h1>
            <p>
              Cấu hình chiết khấu áp dụng cho đặt cọc, thuê kho, gia hạn và các chương trình ưu đãi nhóm khách hàng.
            </p>
          </div>
        </div>
      </section>

      <section className="container discount-content">
        <div className="discount-topbar">
          <div className="discount-topbar__group">
            <button className="button button-primary" type="button" onClick={createNewDiscount}>
              <Plus size={16} /> Tạo discount
            </button>
            <button className="button button-secondary" type="button" onClick={() => navigate("/bom/policies")}>
              <Percent size={16} /> Policy
            </button>
          </div>
        </div>

        <div className="discount-layout">
          <div className="discount-panel">
            <div className="discount-panel__header">
              <div className="discount-panel__title">Danh sách discount</div>
              <span>{filteredDiscounts.length} programs</span>
            </div>

            <label className="filter-field filter-field--search">
              <span>Tìm kiếm</span>
              <div className="filter-input-wrap">
                <Search size={14} />
                <input
                  value={query}
                  onChange={(event) => setQuery(event.target.value)}
                  placeholder="Mã hoặc tên discount"
                />
              </div>
            </label>

            <div className="discount-table-wrap">
              <table className="discount-table">
                <thead>
                  <tr>
                    <th>Code</th>
                    <th>Name</th>
                    <th>Value</th>
                    <th>Status</th>
                  </tr>
                </thead>
                <tbody>
                  {filteredDiscounts.map((discount) => {
                    const isSelected = selectedDiscount?.id === discount.id;
                    return (
                      <tr
                        key={discount.id}
                        className={isSelected ? "discount-row is-selected" : "discount-row"}
                        onClick={() => updateSelected(discount)}
                      >
                        <td>{discount.code}</td>
                        <td>{discount.name}</td>
                        <td>
                          {discount.discountType === "Percent"
                            ? `${discount.value}%`
                            : `${discount.value.toLocaleString("vi-VN")}đ`}
                        </td>
                        <td>
                          <span className={`account-status-tag status-${discount.isActive ? "active" : "inactive"}`}>
                            {discount.isActive ? "Active" : "Inactive"}
                          </span>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
              {!filteredDiscounts.length && <div className="empty-state">Không có discount nào phù hợp.</div>}
            </div>
          </div>

          <aside className="discount-panel discount-panel--detail">
            <div className="discount-detail-header">
              <div>
                <p className="eyebrow">Discount detail</p>
                <h2>{currentDiscount.name || "New discount"}</h2>
              </div>
              <span className={`account-status-tag status-${currentDiscount.isActive ? "active" : "inactive"}`}>
                {currentDiscount.isActive ? "Active" : "Inactive"}
              </span>
            </div>

            <div className="discount-form-grid">
              <div className="discount-field-row">
                <label className="discount-field">
                  <span>Code</span>
                  <input
                    value={currentDiscount.code}
                    onChange={(event) => setDraft({ ...currentDiscount, code: event.target.value })}
                  />
                </label>

                <label className="discount-field">
                  <span>Name</span>
                  <input
                    value={currentDiscount.name}
                    onChange={(event) => setDraft({ ...currentDiscount, name: event.target.value })}
                  />
                </label>
              </div>

              <div className="discount-field-row">
                <label className="discount-field">
                  <span>Type</span>
                  <select
                    value={currentDiscount.discountType}
                    onChange={(event) =>
                      setDraft({ ...currentDiscount, discountType: event.target.value as DiscountType })
                    }
                  >
                    <option value="Percent">Percent</option>
                    <option value="Fixed">Fixed</option>
                  </select>
                </label>

                <label className="discount-field">
                  <span>Value</span>
                  <input
                    type="number"
                    value={currentDiscount.value}
                    onChange={(event) =>
                      setDraft({ ...currentDiscount, value: Number(event.target.value || 0) })
                    }
                  />
                </label>
              </div>

              <div className="discount-field-row">
                <label className="discount-field">
                  <span>Apply to</span>
                  <select
                    value={currentDiscount.applyTo}
                    onChange={(event) => setDraft({ ...currentDiscount, applyTo: event.target.value as ApplyTo })}
                  >
                    <option value="Deposit">Deposit</option>
                    <option value="Rental">Rental</option>
                    <option value="Extension">Extension</option>
                    <option value="All">All</option>
                  </select>
                </label>

                <label className="discount-field">
                  <span>Min months</span>
                  <input
                    type="number"
                    value={currentDiscount.minMonths ?? ""}
                    onChange={(event) =>
                      setDraft({
                        ...currentDiscount,
                        minMonths: event.target.value === "" ? null : Number(event.target.value),
                      })
                    }
                  />
                </label>
              </div>

              <div className="discount-field-row">
                <label className="discount-field">
                  <span>Start date</span>
                  <input
                    type="date"
                    value={currentDiscount.startAt}
                    onChange={(event) => setDraft({ ...currentDiscount, startAt: event.target.value })}
                  />
                </label>

                <label className="discount-field">
                  <span>End date</span>
                  <input
                    type="date"
                    value={currentDiscount.endAt}
                    onChange={(event) => setDraft({ ...currentDiscount, endAt: event.target.value })}
                  />
                </label>
              </div>
            </div>

            {currentDiscount.discountType === "Percent" &&
              (currentDiscount.value < 0 || currentDiscount.value > 100) && (
                <div className="discount-warning">
                  <AlertTriangle size={14} />
                  Mức giảm % phải trong khoảng 0 đến 100.
                </div>
              )}

            {currentDiscount.startAt && currentDiscount.endAt && currentDiscount.startAt >= currentDiscount.endAt && (
              <div className="discount-warning">
                <AlertTriangle size={14} />
                start_at phải nhỏ hơn end_at.
              </div>
            )}

            <div className="discount-actions">
              <button
                className="button button-primary button-full"
                type="button"
                onClick={() => {
                  const nextDiscounts = discounts.some((discount) => discount.id === draft.id)
                    ? discounts.map((discount) => (discount.id === draft.id ? draft : discount))
                    : [draft, ...discounts];
                  setDiscounts(nextDiscounts);
                  setSelectedId(draft.id || `disc-${Date.now()}`);
                }}
              >
                Lưu discount
              </button>

              <button
                className="button button-secondary button-full"
                type="button"
                onClick={() => {
                  setDraft({ ...currentDiscount, isActive: true });
                  setDiscounts((current) =>
                    current.map((discount) =>
                      discount.id === currentDiscount.id ? { ...discount, isActive: true } : discount,
                    ),
                  );
                }}
              >
                Activate
              </button>

              <button
                className="button button-full discount-secondary-button"
                type="button"
                onClick={() => {
                  setDraft({ ...currentDiscount, isActive: false });
                  setDiscounts((current) =>
                    current.map((discount) =>
                      discount.id === currentDiscount.id ? { ...discount, isActive: false } : discount,
                    ),
                  );
                }}
              >
                Deactivate
              </button>
            </div>
          </aside>
        </div>
      </section>
    </main>
  );
}
