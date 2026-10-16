/**
 * Upload file lên object storage (Cloudinary) — ảnh hiện trạng, scan hợp đồng, ảnh giấy tờ.
 *
 * <p>FE gọi qua endpoint {@code POST /api/media/uploads} và dùng URL trả về khi submit flow;
 * chưa có server-side upload từ module khác nên module chưa mở named interface.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Media")
package vn.lemar.selfstorage.media;
