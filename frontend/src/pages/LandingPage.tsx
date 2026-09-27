import { useState } from "react";
import {
  ArrowRight,
  Box,
  Check,
  ChevronDown,
  ChevronRight,
  Clock3,
  MapPin,
  Search,
} from "lucide-react";
import { Navigate } from "../app/types";
import { facilities, formatPrice, unitTypes } from "../mocks/catalog";

export function LandingPage({ navigate }: { navigate: Navigate }) {
  const [facilityId, setFacilityId] = useState("");
  const [typeId, setTypeId] = useState("");
  const [selectedFacilityId, setSelectedFacilityId] = useState(facilities[0]?.id ?? "");
  const selectedFacility =
    facilities.find((item) => item.id === selectedFacilityId) ?? facilities[0];
  const selectedUnits = selectedFacility
    ? unitTypes.filter((unit) => selectedFacility.availableUnitIds.includes(unit.id))
    : [];

  function browseFromFinder(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const params = new URLSearchParams();
    if (facilityId) params.set("facility", facilityId);
    if (typeId) params.set("type", typeId);
    const query = params.toString();
    navigate(`/units${query ? `?${query}` : ""}`);
  }

  return (
    <>
      <section className="landing-hero">
        <div className="container landing-hero-grid">
          <div className="landing-hero-copy">
            <h1>
              Tìm đúng chỗ chứa
              <span>cho nhịp hàng.</span>
            </h1>
            <p>
              Chọn điểm kho và quy mô để xem phương án phù hợp trước khi gửi nhu cầu. Không cần đăng
              nhập để bắt đầu.
            </p>
            <div className="landing-hero-links">
              <a className="landing-text-link" href="#how-it-works">
                Xem quy trình <ArrowRight size={16} />
              </a>
            </div>
          </div>

          <form className="landing-finder" onSubmit={browseFromFinder}>
            <div className="landing-finder-heading">
              <div className="landing-finder-icon" aria-hidden="true">
                <Search size={17} />
              </div>
              <div>
                <p className="landing-finder-label">Bắt đầu từ điều bạn biết</p>
                <h2>Tìm phương án kho</h2>
              </div>
            </div>
            <p className="landing-finder-copy">
              Chọn một hoặc cả hai tiêu chí. Bạn có thể xem toàn bộ lựa chọn sau đó.
            </p>

            <label className="landing-field" htmlFor="landing-facility">
              <span>Điểm kho</span>
              <div className="landing-select-wrap">
                <MapPin size={16} aria-hidden="true" />
                <select
                  id="landing-facility"
                  value={facilityId}
                  onChange={(event) => setFacilityId(event.target.value)}
                >
                  <option value="">Tất cả điểm kho</option>
                  {facilities.map((facility) => (
                    <option key={facility.id} value={facility.id}>
                      {facility.district}
                    </option>
                  ))}
                </select>
                <ChevronDown size={15} aria-hidden="true" />
              </div>
            </label>

            <label className="landing-field" htmlFor="landing-unit-type">
              <span>Quy mô lưu trữ</span>
              <div className="landing-select-wrap">
                <Box size={16} aria-hidden="true" />
                <select
                  id="landing-unit-type"
                  value={typeId}
                  onChange={(event) => setTypeId(event.target.value)}
                >
                  <option value="">Tất cả quy mô</option>
                  {unitTypes.map((unit) => (
                    <option key={unit.id} value={unit.id}>
                      {unit.capacity} · {unit.name}
                    </option>
                  ))}
                </select>
                <ChevronDown size={15} aria-hidden="true" />
              </div>
            </label>

            <button className="landing-button landing-button-primary" type="submit">
              Xem phương án kho <ArrowRight size={16} />
            </button>
            <p className="landing-finder-footnote">
              <Check size={14} aria-hidden="true" /> Xem trước không cần đăng nhập
            </p>
          </form>
        </div>

        <div className="container landing-proof-row" aria-label="Điều bạn có thể làm trên Kho Mộc">
          <div className="landing-proof-item">
            <span className="landing-proof-mark" aria-hidden="true">
              01
            </span>
            <span>
              <strong>Chọn trước</strong>
              <small>Điểm kho và quy mô</small>
            </span>
          </div>
          <div className="landing-proof-item">
            <span className="landing-proof-mark" aria-hidden="true">
              02
            </span>
            <span>
              <strong>So sánh rõ</strong>
              <small>Kích thước, giá tham khảo</small>
            </span>
          </div>
          <div className="landing-proof-item">
            <span className="landing-proof-mark" aria-hidden="true">
              03
            </span>
            <span>
              <strong>Gửi khi sẵn sàng</strong>
              <small>Không cần cam kết ngay</small>
            </span>
          </div>
        </div>
      </section>

      <section className="landing-facilities" id="support">
        <div className="container">
          <div className="landing-section-heading">
            <div>
              <h2>Xem nơi hàng đến, rồi chọn quy mô.</h2>
            </div>
            <p>
              Thông tin địa chỉ, giờ tiếp nhận và các phương án mẫu được đặt cạnh nhau để bạn kiểm
              tra nhanh.
            </p>
          </div>

          <div className="landing-directory">
            <div className="landing-facility-list" aria-label="Chọn điểm kho">
              <div className="landing-list-heading">
                <span>Điểm kho</span>
                <span>{String(facilities.length).padStart(2, "0")}</span>
              </div>
              {facilities.map((facility, index) => {
                const isSelected = facility.id === selectedFacility?.id;
                return (
                  <button
                    key={facility.id}
                    className={`landing-facility-option ${isSelected ? "is-selected" : ""}`}
                    type="button"
                    aria-pressed={isSelected}
                    onClick={() => setSelectedFacilityId(facility.id)}
                  >
                    <span className="landing-facility-index">
                      {String(index + 1).padStart(2, "0")}
                    </span>
                    <span className="landing-facility-option-copy">
                      <strong>{facility.district}</strong>
                      <small>{facility.address}</small>
                    </span>
                    <ChevronRight size={16} aria-hidden="true" />
                  </button>
                );
              })}
            </div>

            {selectedFacility && (
              <article className="landing-facility-detail" aria-live="polite">
                <div className="landing-facility-detail-top">
                  <div>
                    <span className="landing-detail-label">Đang xem</span>
                    <h3>{selectedFacility.name}</h3>
                  </div>
                  <span className="landing-facility-status">Đang hoạt động</span>
                </div>
                <p className="landing-facility-note">{selectedFacility.note}</p>
                <div className="landing-facility-meta">
                  <span>
                    <MapPin size={15} /> {selectedFacility.address}
                  </span>
                  <span>
                    <Clock3 size={15} /> {selectedFacility.hours.replace("Tiếp nhận hàng: ", "")}
                  </span>
                </div>

                <div className="landing-unit-list">
                  <div className="landing-list-heading">
                    <span>Quy mô tham khảo</span>
                    <span>{String(selectedUnits.length).padStart(2, "0")}</span>
                  </div>
                  {selectedUnits.map((unit) => (
                    <div className="landing-unit-row" key={unit.id}>
                      <div className="landing-unit-signal" aria-hidden="true">
                        <Box size={17} />
                      </div>
                      <div className="landing-unit-copy">
                        <strong>{unit.name}</strong>
                        <span>
                          {unit.capacity} · {unit.size}
                        </span>
                      </div>
                      <div className="landing-unit-price">
                        <strong>{formatPrice(unit.monthlyPrice)}đ</strong>
                        <span>/ tháng</span>
                      </div>
                      <button
                        className="landing-row-link"
                        type="button"
                        onClick={() =>
                          navigate(`/units?facility=${selectedFacility.id}&type=${unit.id}`)
                        }
                      >
                        Chi tiết <ArrowRight size={14} />
                      </button>
                    </div>
                  ))}
                </div>

                <div className="landing-detail-actions">
                  <button
                    className="landing-button landing-button-secondary"
                    type="button"
                    onClick={() => navigate(`/units?facility=${selectedFacility.id}`)}
                  >
                    Xem tất cả phương án <ArrowRight size={16} />
                  </button>
                </div>
              </article>
            )}
          </div>
        </div>
      </section>

      <section className="landing-process" id="how-it-works">
        <div className="container landing-process-grid">
          <div>
            <h2>Từ lựa chọn đến nhu cầu rõ ràng.</h2>
            <p className="landing-process-intro">
              Bạn luôn biết mình đang ở bước nào và cần chuẩn bị điều gì tiếp theo.
            </p>
          </div>
          <div className="landing-process-list">
            <article>
              <span className="landing-process-number">01</span>
              <div>
                <h3>Xác định nhu cầu</h3>
                <p>Chọn khu vực và quy mô lưu trữ gần với kế hoạch hàng hóa của bạn.</p>
              </div>
            </article>
            <article>
              <span className="landing-process-number">02</span>
              <div>
                <h3>Gửi thông tin</h3>
                <p>Cho Kho Mộc biết lô hàng, thời gian và nhu cầu vận hành dự kiến.</p>
              </div>
            </article>
            <article>
              <span className="landing-process-number">03</span>
              <div>
                <h3>Chốt phương án</h3>
                <p>Đội ngũ phản hồi, thống nhất lịch và các bước tiếp theo.</p>
              </div>
            </article>
          </div>
        </div>
      </section>

      <section className="landing-final-cta">
        <div className="container landing-final-cta-inner">
          <div>
            <h2>Đã biết quy mô?</h2>
            <p>Gửi nhu cầu lưu trữ khi bạn đã sẵn sàng.</p>
          </div>
          <button
            className="landing-button landing-button-primary"
            type="button"
            onClick={() => navigate("/rental-requests/new")}
          >
            Gửi nhu cầu lưu trữ <ArrowRight size={16} />
          </button>
        </div>
      </section>
    </>
  );
}
