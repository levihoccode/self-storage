import { CheckCircle2, CircleDashed, Search, Wrench } from "lucide-react";
import { useMemo, useState } from "react";
import type { Navigate } from "../../app/types";

type SupportStatus = "Assigned" | "InProgress" | "Resolved";

type SupportItem = {
  id: string;
  customer: string;
  unit: string;
  issue: string;
  priority: "Normal" | "High";
  status: SupportStatus;
};

const initialRequests: SupportItem[] = [
  {
    id: "sr-201",
    customer: "Nguyễn Hoàng Long",
    unit: "B-05",
    issue: "Ổ khóa kẹt khi vào kho",
    priority: "High",
    status: "Assigned",
  },
  {
    id: "sr-202",
    customer: "Lê An Nhiên",
    unit: "A-09",
    issue: "Không thể mở cửa sau giờ 18h",
    priority: "Normal",
    status: "InProgress",
  },
  {
    id: "sr-203",
    customer: "Trần Tấn Đạt",
    unit: "C-02",
    issue: "Sàn kho bị ẩm, cần kiểm tra nhanh",
    priority: "High",
    status: "Resolved",
  },
];

export function SupportRequestHandling({ navigate }: { navigate: Navigate }) {
  const [query, setQuery] = useState("");
  const [statusFilter, setStatusFilter] = useState<"all" | SupportStatus>("all");

  const filteredRequests = useMemo(() => {
    const keyword = query.trim().toLowerCase();
    return initialRequests.filter((item) => {
      const matchesStatus = statusFilter === "all" || item.status === statusFilter;
      const matchesQuery =
        !keyword ||
        item.customer.toLowerCase().includes(keyword) ||
        item.unit.toLowerCase().includes(keyword) ||
        item.issue.toLowerCase().includes(keyword);
      return matchesStatus && matchesQuery;
    });
  }, [query, statusFilter]);

  return (
    <main className="fs-page">
      <section className="fs-hero">
        <div className="container fs-hero__inner">
          <div>
            <p className="eyebrow">Support queue</p>
            <h1>
              Xử lý <span>hỗ trợ.</span>
            </h1>
            <p>Đơn yêu cầu hỗ trợ được FM phân công cho bạn sẽ được theo dõi và xử lý trong đây.</p>
          </div>
        </div>
      </section>

      <section className="container fs-page-content">
        <div className="fs-panel-toolbar">
          <div className="fs-toolbar-block">
            <Wrench size={18} />
            <strong>Danh sách hỗ trợ</strong>
          </div>
          <div className="fs-toolbar-actions">
            <label className="fs-search-field">
              <Search size={14} />
              <input
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                placeholder="Tìm đơn, khoang, khách"
              />
            </label>
            <select
              value={statusFilter}
              onChange={(e) => setStatusFilter(e.target.value as typeof statusFilter)}
            >
              <option value="all">Tất cả</option>
              <option value="Assigned">Assigned</option>
              <option value="InProgress">InProgress</option>
              <option value="Resolved">Resolved</option>
            </select>
          </div>
        </div>

        <div className="fs-table-wrap">
          <table className="fs-table">
            <thead>
              <tr>
                <th>Khách hàng</th>
                <th>Khoang</th>
                <th>Vấn đề</th>
                <th>Ưu tiên</th>
                <th>Trạng thái</th>
              </tr>
            </thead>
            <tbody>
              {filteredRequests.map((item) => (
                <tr key={item.id}>
                  <td>{item.customer}</td>
                  <td>{item.unit}</td>
                  <td>{item.issue}</td>
                  <td>
                    <span
                      className={`fs-tag ${item.priority === "High" ? "tag-warning" : "tag-neutral"}`}
                    >
                      {item.priority}
                    </span>
                  </td>
                  <td>
                    <span
                      className={`fs-tag ${item.status === "Resolved" ? "tag-success" : item.status === "InProgress" ? "tag-info" : "tag-neutral"}`}
                    >
                      {item.status}
                    </span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        {!filteredRequests.length && (
          <div className="empty-state">
            Không có đơn hỗ trợ nào phù hợp với điều kiện lọc hiện tại.
          </div>
        )}

        <div className="fs-actions-row">
          <button
            className="button button-primary"
            type="button"
            onClick={() => navigate("/fs/incidents/new")}
          >
            <CircleDashed size={16} /> Ghi nhận sự cố mới
          </button>
          <button
            className="button button-secondary"
            type="button"
            onClick={() => navigate("/fs/schedule")}
          >
            <CheckCircle2 size={16} /> Về lịch làm việc
          </button>
        </div>
      </section>
    </main>
  );
}
