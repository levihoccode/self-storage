import { ArrowRight, CalendarDays, MapPin, ReceiptText } from "lucide-react";
import { useMemo, useState } from "react";
import type { Navigate } from "../app/types";
import { rentedStorage, StorageStatus } from "../mocks/customer";

const statusLabel: Record<StorageStatus, string> = {
  active: "Đang thuê",
  expiring: "Sắp hết hạn",
  overdue: "Đã quá hạn",
};

export function MyStoragePage({ navigate }: { navigate: Navigate }) {
  const [facility, setFacility] = useState("all");
  const [sort, setSort] = useState("nearest");
  const facilities = useMemo(
    () => [...new Set(rentedStorage.map((storage) => storage.facility))],
    [],
  );
  const filteredStorage = useMemo(() => {
    const result = rentedStorage.filter(
      (storage) => facility === "all" || storage.facility === facility,
    );
    return [...result].sort((left, right) => {
      const leftDate = Date.parse(left.endDate.split("/").reverse().join("-"));
      const rightDate = Date.parse(right.endDate.split("/").reverse().join("-"));
      return sort === "nearest" ? leftDate - rightDate : rightDate - leftDate;
    });
  }, [facility, sort]);

  return (
    <main className="storage-page">
      <div className="page-intro storage-intro"></div>

      <section className="storage-list-section" aria-labelledby="storage-list-title">
        <div className="section-heading-row">
          <button
            className="button button-primary"
            onClick={() => navigate("/rental-requests/new")}
          >
            Thuê thêm kho <ArrowRight size={17} />
          </button>
          <div className="storage-filters">
            <label>
              Chi nhánh
              <select value={facility} onChange={(event) => setFacility(event.target.value)}>
                <option value="all">Tất cả chi nhánh</option>
                {facilities.map((item) => (
                  <option key={item} value={item}>
                    {item.replace("Kho Mộc — ", "")}
                  </option>
                ))}
              </select>
            </label>
            <label>
              Sắp xếp
              <select value={sort} onChange={(event) => setSort(event.target.value)}>
                <option value="nearest">Sắp hết hạn trước</option>
                <option value="farthest">Hạn xa nhất trước</option>
              </select>
            </label>
          </div>
        </div>
        <div className="storage-list">
          {filteredStorage.map((storage) => (
            <StorageCard key={storage.id} storage={storage} navigate={navigate} />
          ))}
        </div>
      </section>
    </main>
  );
}

function StorageCard({
  storage,
  navigate,
}: {
  storage: (typeof rentedStorage)[number];
  navigate: Navigate;
}) {
  return (
    <article className="storage-card">
      <div className="storage-card-main">
        <div className="storage-card-heading">
          <div>
            <p className="eyebrow">Khoang {storage.unitCode}</p>
            <h3>{storage.facility}</h3>
          </div>
          <span className={`status-badge status-${storage.status}`}>
            {statusLabel[storage.status]}
          </span>
        </div>
        <p className="storage-location">
          <MapPin size={16} /> {storage.district}
        </p>
        <div className="storage-details">
          <div>
            <span>Diện tích</span>
            <strong>{storage.size}</strong>
          </div>
          <div>
            <span>Thời hạn đến</span>
            <strong>{storage.endDate}</strong>
          </div>
          <div>
            <span>Giá thuê</span>
            <strong>{storage.monthlyPrice}</strong>
          </div>
        </div>
      </div>
      <div className="storage-card-side">
        <div className="storage-payment">
          <ReceiptText size={16} />
          <span>{storage.paymentLabel}</span>
        </div>
        <div className="storage-actions">
          <button
            className="button button-secondary"
            onClick={() => navigate(`/my-storage/${storage.id}`)}
          >
            Xem chi tiết <ArrowRight size={16} />
          </button>
          <button className="button button-quiet" onClick={() => navigate("/appointments")}>
            <CalendarDays size={16} /> Lịch hẹn
          </button>
        </div>
      </div>
    </article>
  );
}
