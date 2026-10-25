import { authGateway } from "./auth";

/**
 * TẠM — chưa có route trả cơ sở của FM đang đăng nhập (levihoccode, #71). BE chỉ quan hệ qua
 * `facilities.fm_account_id`; tạm map theo seed: fm1 → Q7 (id 1), fm2 → TD (id 2). BE vẫn kiểm
 * quyền theo cơ sở nên map sai chỉ ra 403, không lộ dữ liệu. Bỏ khi có `GET /api/fm/facility`.
 */
const FACILITY_ID_BY_FM_EMAIL: Record<string, number> = {
  "fm1@lemar.vn": 1,
  "fm2@lemar.vn": 2,
};

export function currentFmFacilityId(): number | null {
  const email = authGateway.getSession()?.user.email.toLowerCase();
  return email ? (FACILITY_ID_BY_FM_EMAIL[email] ?? null) : null;
}
