import { useMemo, useState } from "react";
import { CheckCircle2, Plus, Send, UserRound } from "lucide-react";
import type { Navigate } from "../../app/types";
import "./StaffRoleRequest.css";

type RequestRole = "FM" | "FS";
type RequestStatus = "Pending" | "Done";

type RoleRequestRow = {
  id: string;
  employeeName: string;
  email: string;
  role: RequestRole;
  targetFacility: string;
  status: RequestStatus;
  notes: string;
};

const initialRows: RoleRequestRow[] = [
  {
    id: "req-101",
    employeeName: "Nguyễn Văn Hà",
    email: "ha.nguyen@storage.vn",
    role: "FM",
    targetFacility: "Q7",
    status: "Pending",
    notes: "Cần hỗ trợ vận hành khu Q7",
  },
  {
    id: "req-102",
    employeeName: "Trần Thị Hương",
    email: "huong.tran@storage.vn",
    role: "FS",
    targetFacility: "Q9",
    status: "Done",
    notes: "Đã gán facility và account thành công",
  },
  {
    id: "req-103",
    employeeName: "Lê Hoàng Nam",
    email: "nam.le@storage.vn",
    role: "FM",
    targetFacility: "Q12",
    status: "Pending",
    notes: "Chờ Admin xử lý cập nhật tài khoản",
  },
];

const emptyRow: RoleRequestRow = {
  id: "",
  employeeName: "",
  email: "",
  role: "FM",
  targetFacility: "",
  status: "Pending",
  notes: "",
};

export function StaffRoleRequest({ navigate }: { navigate: Navigate }) {
  const [rows, setRows] = useState<RoleRequestRow[]>(initialRows);
  const [draftRows, setDraftRows] = useState<RoleRequestRow[]>(initialRows);

  const summary = useMemo(() => {
    return rows.reduce(
      (acc, row) => {
        acc[row.status] += 1;
        return acc;
      },
      { Pending: 0, Done: 0 },
    );
  }, [rows]);

  function updateRow(rowId: string, field: keyof RoleRequestRow, value: string | RequestRole | RequestStatus) {
    setDraftRows((current) =>
      current.map((row) => (row.id === rowId ? { ...row, [field]: value } : row)),
    );
  }

  function addRow() {
    const newRow: RoleRequestRow = {
      ...emptyRow,
      id: `req-${Date.now()}`,
      employeeName: "New staff",
      email: "new.staff@storage.vn",
      targetFacility: "Q7",
      status: "Pending",
    };
    setDraftRows((current) => [...current, newRow]);
  }

  function submitBatch() {
    setRows(draftRows.map((row) => ({ ...row, status: row.status || "Pending" })));
  }

  function removeRow(rowId: string) {
    setDraftRows((current) => current.filter((row) => row.id !== rowId));
  }

  return (
    <main className="staff-request-page" style={{ position: "relative" }}>
      <section className="staff-request-hero">
        <div className="container staff-request-hero__inner">
          <div>
            <p className="eyebrow">Business Operation Manager</p>
            <h1>
              Staff <span>role request.</span>
            </h1>
            <p>
              BOM xác định role và gán facility cho nhân sự; Admin sẽ thực thi kỹ thuật tạo/cập nhật account.
            </p>
          </div>
        </div>
      </section>

      <section className="container staff-request-content">
        <div className="staff-request-summary">
          <div className="summary-card">
            <span>Pending</span>
            <strong>{summary.Pending}</strong>
          </div>
          <div className="summary-card">
            <span>Done</span>
            <strong>{summary.Done}</strong>
          </div>
          <div className="summary-card">
            <span>Batch</span>
            <strong>{draftRows.length}</strong>
          </div>
        </div>

        <div className="staff-request-topbar">
          <div className="staff-request-topbar__group">
            <button className="button button-primary" type="button" onClick={addRow}>
              <Plus size={16} /> Thêm dòng
            </button>
            <button className="button button-secondary" type="button" onClick={() => navigate("/bom/facilities")}>
              <UserRound size={16} /> Facility
            </button>
          </div>
          <button className="button button-primary" type="button" onClick={submitBatch}>
            <Send size={16} /> Gửi lên Admin
          </button>
        </div>

        <div className="staff-request-panel">
          <div className="staff-request-panel__header">
            <div className="staff-request-panel__title">Phân bổ nhân sự</div>
            <span>{draftRows.length} records</span>
          </div>

          <div className="request-table-wrap">
            <table className="request-table">
              <thead>
                <tr>
                  <th>Họ tên</th>
                  <th>Email</th>
                  <th>Role</th>
                  <th>Facility</th>
                  <th>Status</th>
                  <th>Note</th>
                  <th> </th>
                </tr>
              </thead>
              <tbody>
                {draftRows.map((row) => (
                  <tr key={row.id} className="request-row">
                    <td>
                      <input
                        value={row.employeeName}
                        onChange={(event) => updateRow(row.id, "employeeName", event.target.value)}
                      />
                    </td>
                    <td>
                      <input
                        value={row.email}
                        onChange={(event) => updateRow(row.id, "email", event.target.value)}
                      />
                    </td>
                    <td>
                      <select
                        value={row.role}
                        onChange={(event) => updateRow(row.id, "role", event.target.value as RequestRole)}
                      >
                        <option value="FM">FM</option>
                        <option value="FS">FS</option>
                      </select>
                    </td>
                    <td>
                      <input
                        value={row.targetFacility}
                        onChange={(event) => updateRow(row.id, "targetFacility", event.target.value)}
                      />
                    </td>
                    <td>
                      <select
                        value={row.status}
                        onChange={(event) => updateRow(row.id, "status", event.target.value as RequestStatus)}
                      >
                        <option value="Pending">Pending</option>
                        <option value="Done">Done</option>
                      </select>
                    </td>
                    <td>
                      <input
                        value={row.notes}
                        onChange={(event) => updateRow(row.id, "notes", event.target.value)}
                      />
                    </td>
                    <td>
                      <button className="button button-ghost" type="button" onClick={() => removeRow(row.id)}>
                        Xóa
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>

        <div className="staff-request-panel staff-request-panel--small">
          <div className="staff-request-panel__header">
            <div className="staff-request-panel__title">Batch status</div>
            <CheckCircle2 size={16} />
          </div>

          <div className="batch-status">
            <div className="batch-status__item">
              <span>Pending</span>
              <strong>{summary.Pending}</strong>
            </div>
            <div className="batch-status__item">
              <span>Done</span>
              <strong>{summary.Done}</strong>
            </div>
          </div>
        </div>
      </section>
    </main>
  );
}
