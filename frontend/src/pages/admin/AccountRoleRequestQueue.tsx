import { useMemo, useState } from "react";
import { ArrowRight, CheckCheck, Clock3, FileWarning, Search, Users } from "lucide-react";
import type { Navigate } from "../../app/types";
import "./AccountManagement.css";

type RequestStatus = "Pending" | "Done";
type RequestRole = "FM" | "FS";

type AccountRoleRequest = {
  id: string;
  batchId: string;
  targetName: string;
  targetEmail: string;
  role: RequestRole;
  facility: string;
  requestedBy: string;
  createdAt: string;
  status: RequestStatus;
  note?: string;
};

const initialRequests: AccountRoleRequest[] = [
  {
    id: "rq-201",
    batchId: "BATCH-07",
    targetName: "Huỳnh Thị Mai",
    targetEmail: "mai.huynh@khomoc.vn",
    role: "FS",
    facility: "Kho Mộc Tân Bình",
    requestedBy: "BOM - Lê Thị Lan",
    createdAt: "2026-09-22 09:15",
    status: "Pending",
  },
  {
    id: "rq-202",
    batchId: "BATCH-07",
    targetName: "Nguyễn Văn Dũng",
    targetEmail: "dung.nguyen@khomoc.vn",
    role: "FM",
    facility: "Kho Mộc Thảo Điền",
    requestedBy: "BOM - Lê Thị Lan",
    createdAt: "2026-09-22 09:42",
    status: "Pending",
  },
  {
    id: "rq-203",
    batchId: "BATCH-08",
    targetName: "Phan Quốc Hùng",
    targetEmail: "hung.phan@invalid-domain",
    role: "FS",
    facility: "Kho Mộc Quận 7",
    requestedBy: "BOM - Đặng Minh Khoa",
    createdAt: "2026-09-23 08:10",
    status: "Pending",
    note: "Email sai định dạng, cần xác minh lại trước khi thực thi.",
  },
  {
    id: "rq-204",
    batchId: "BATCH-09",
    targetName: "Trương Ngọc Nhi",
    targetEmail: "nhi.truong@khomoc.vn",
    role: "FS",
    facility: "Kho Mộc Bình Thạnh",
    requestedBy: "BOM - Đặng Minh Khoa",
    createdAt: "2026-09-23 11:25",
    status: "Done",
  },
];

const roleOptions: Array<"all" | RequestRole> = ["all", "FM", "FS"];
const statusOptions: Array<"all" | RequestStatus> = ["all", "Pending", "Done"];

export function AccountRoleRequestQueue({ navigate }: { navigate: Navigate }) {
  const [requests, setRequests] = useState<AccountRoleRequest[]>(initialRequests);
  const [roleFilter, setRoleFilter] = useState<"all" | RequestRole>("all");
  const [statusFilter, setStatusFilter] = useState<"all" | RequestStatus>("all");
  const [query, setQuery] = useState("");
  const [selectedId, setSelectedId] = useState(
    initialRequests.find((item) => item.status === "Pending")?.id ?? "",
  );

  const filteredRequests = useMemo(() => {
    const keyword = query.trim().toLowerCase();

    return requests.filter((request) => {
      const matchesRole = roleFilter === "all" || request.role === roleFilter;
      const matchesStatus = statusFilter === "all" || request.status === statusFilter;
      const matchesSearch =
        !keyword ||
        request.targetName.toLowerCase().includes(keyword) ||
        request.targetEmail.toLowerCase().includes(keyword) ||
        request.facility.toLowerCase().includes(keyword) ||
        request.batchId.toLowerCase().includes(keyword);

      return matchesRole && matchesStatus && matchesSearch;
    });
  }, [query, requests, roleFilter, statusFilter]);

  const selectedRequest =
    filteredRequests.find((request) => request.id === selectedId) ?? filteredRequests[0] ?? null;

  function executeRequest(request: AccountRoleRequest) {
    setRequests((current) =>
      current.map((item) =>
        item.id === request.id
          ? { ...item, status: "Done", note: item.note ?? "Đã thực thi bởi Admin." }
          : item,
      ),
    );
  }

  return (
    <main className="admin-account-page">
      <section className="admin-account-hero">
        <div className="container admin-account-hero__inner">
          <div>
            <p className="eyebrow">System Administrator</p>
            <h1>
              Hàng chờ <span>role request.</span>
            </h1>
            <p>
              Thực thi yêu cầu bổ nhiệm vai trò do BOM gửi lên, đảm bảo dữ liệu được cập nhật trong
              một giao dịch duy nhất.
            </p>
          </div>
        </div>
      </section>

      <section className="container admin-account-content">
        <div className="admin-account-actions">
          <div className="admin-account-action-group">
            <button
              className="button button-primary"
              type="button"
              onClick={() => navigate("/admin/accounts")}
            >
              <Users size={16} /> Quản lý tài khoản
            </button>
            <button
              className="button button-secondary"
              type="button"
              onClick={() => navigate("/admin/audit-log")}
            >
              <Clock3 size={16} /> Audit log
            </button>
          </div>

          <button
            className="button button-quiet"
            type="button"
            onClick={() => navigate("/notifications")}
          >
            <FileWarning size={16} /> Thông báo
          </button>
        </div>

        <div className="admin-account-layout">
          <div className="admin-account-panel admin-account-panel--list">
            <div className="admin-account-toolbar">
              <div className="admin-account-toolbar__title">
                <Clock3 size={18} />
                <strong>Danh sách role request</strong>
              </div>
              <span className="admin-account-toolbar__count">
                {filteredRequests.filter((item) => item.status === "Pending").length} pending
              </span>
            </div>

            <div className="admin-account-filters">
              <label className="filter-field filter-field--search">
                <span>Tìm kiếm</span>
                <div className="filter-input-wrap">
                  <Search size={14} />
                  <input
                    value={query}
                    onChange={(event) => setQuery(event.target.value)}
                    placeholder="Tên, email, batch, facility"
                  />
                </div>
              </label>

              <label className="filter-field">
                <span>Role</span>
                <select
                  value={roleFilter}
                  onChange={(event) => setRoleFilter(event.target.value as typeof roleFilter)}
                >
                  {roleOptions.map((option) => (
                    <option key={option} value={option}>
                      {option === "all" ? "Tất cả" : option}
                    </option>
                  ))}
                </select>
              </label>

              <label className="filter-field">
                <span>Status</span>
                <select
                  value={statusFilter}
                  onChange={(event) => setStatusFilter(event.target.value as typeof statusFilter)}
                >
                  {statusOptions.map((option) => (
                    <option key={option} value={option}>
                      {option === "all" ? "Tất cả" : option}
                    </option>
                  ))}
                </select>
              </label>
            </div>

            <div className="account-table-wrapper">
              <table className="account-table">
                <thead>
                  <tr>
                    <th>Batch</th>
                    <th>Người nhận</th>
                    <th>Role</th>
                    <th>Status</th>
                  </tr>
                </thead>
                <tbody>
                  {filteredRequests.map((request) => {
                    const isSelected = selectedRequest?.id === request.id;
                    return (
                      <tr
                        key={request.id}
                        className={isSelected ? "account-row is-selected" : "account-row"}
                        onClick={() => setSelectedId(request.id)}
                      >
                        <td>
                          <div className="account-user">
                            <strong>{request.batchId}</strong>
                            <span>{request.createdAt}</span>
                          </div>
                        </td>
                        <td>
                          <div className="account-user">
                            <strong>{request.targetName}</strong>
                            <span>{request.targetEmail}</span>
                          </div>
                        </td>
                        <td>
                          <span className={`account-role-tag role-${request.role.toLowerCase()}`}>
                            {request.role}
                          </span>
                        </td>
                        <td>
                          <span
                            className={`account-status-tag status-${request.status.toLowerCase()}`}
                          >
                            {request.status}
                          </span>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>

              {!filteredRequests.length && (
                <div className="empty-state">
                  Không có role request nào phù hợp với bộ lọc hiện tại.
                </div>
              )}
            </div>
          </div>

          <aside className="admin-account-panel admin-account-panel--detail">
            {selectedRequest ? (
              <>
                <div className="admin-account-detail-header">
                  <div>
                    <p className="eyebrow">Request detail</p>
                    <h2>{selectedRequest.targetName}</h2>
                  </div>
                  <span
                    className={`account-status-tag status-${selectedRequest.status.toLowerCase()}`}
                  >
                    {selectedRequest.status}
                  </span>
                </div>

                <div className="admin-account-detail-grid">
                  <div className="detail-field">
                    <span>Email</span>
                    <strong>{selectedRequest.targetEmail}</strong>
                  </div>
                  <div className="detail-field">
                    <span>Role</span>
                    <strong
                      className={`detail-role detail-role--${selectedRequest.role.toLowerCase()}`}
                    >
                      {selectedRequest.role}
                    </strong>
                  </div>
                  <div className="detail-field">
                    <span>Facility</span>
                    <strong>{selectedRequest.facility}</strong>
                  </div>
                  <div className="detail-field">
                    <span>Requested by</span>
                    <strong>{selectedRequest.requestedBy}</strong>
                  </div>
                  <div className="detail-field">
                    <span>Batch</span>
                    <strong>{selectedRequest.batchId}</strong>
                  </div>
                </div>

                {selectedRequest.note && (
                  <div className="admin-account-warning">{selectedRequest.note}</div>
                )}

                <div className="admin-account-actions-panel">
                  <button
                    className="button button-primary button-full"
                    type="button"
                    onClick={() => executeRequest(selectedRequest)}
                    disabled={selectedRequest.status === "Done"}
                  >
                    <CheckCheck size={16} />{" "}
                    {selectedRequest.status === "Done" ? "Đã thực thi" : "Thực thi request"}
                  </button>

                  <button
                    className="button button-full admin-account-secondary-button"
                    type="button"
                    onClick={() => navigate("/admin/accounts")}
                  >
                    Xem tài khoản <ArrowRight size={16} />
                  </button>
                </div>
              </>
            ) : (
              <div className="empty-state">Không có request nào trong hàng chờ.</div>
            )}
          </aside>
        </div>
      </section>
    </main>
  );
}
