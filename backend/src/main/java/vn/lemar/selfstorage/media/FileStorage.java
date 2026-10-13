package vn.lemar.selfstorage.media;

/**
 * Cổng lưu trữ file — cô lập provider theo issue #15 §6 (impl hiện tại: Cloudinary).
 */
public interface FileStorage {

    /** Kết quả một lần lưu file. */
    record StoredFile(String url, String publicId, long bytes, String format) {}

    /**
     * Lưu file lên storage và trả URL đọc lại được.
     *
     * @throws vn.lemar.selfstorage.media.application.exception.MediaStorageException lỗi provider
     */
    StoredFile store(byte[] content, String originalFilename, String contentType);
}
