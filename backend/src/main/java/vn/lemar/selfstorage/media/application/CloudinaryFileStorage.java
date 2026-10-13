package vn.lemar.selfstorage.media.application;

import com.cloudinary.Cloudinary;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import vn.lemar.selfstorage.media.FileStorage;
import vn.lemar.selfstorage.media.application.exception.InvalidUploadException;
import vn.lemar.selfstorage.media.application.exception.MediaStorageException;

/**
 * Impl Cloudinary — {@code resource_type=image} để Cloudinary tự kiểm nội dung file (PDF cũng
 * nhận ở image type); tránh {@code auto} vì file rác sẽ bị lưu dạng raw thay vì bị từ chối.
 */
@Component
public class CloudinaryFileStorage implements FileStorage {

    private static final Logger LOG = LoggerFactory.getLogger(CloudinaryFileStorage.class);

    private final Cloudinary cloudinary;

    public CloudinaryFileStorage(Cloudinary cloudinary) {
        this.cloudinary = cloudinary;
    }

    @Override
    public StoredFile store(byte[] content, String originalFilename, String contentType, String folder) {
        Map<String, Object> options = new HashMap<>();
        options.put("resource_type", "image");
        if (folder != null && !folder.isBlank()) {
            options.put("folder", folder);
        }
        Map<?, ?> result;
        try {
            result = cloudinary.uploader().upload(content, options);
        } catch (IOException e) {
            LOG.error("Upload Cloudinary thất bại (network), file={}", originalFilename, e);
            throw new MediaStorageException("Không tải được file lên storage", e);
        } catch (RuntimeException e) {
            // Upload API trả 4xx kèm message (vd "Invalid image file") và SDK ném RuntimeException.
            // Cloudinary 5xx hiếm cũng rơi vào đây — log để phân biệt khi vận hành.
            LOG.warn("Cloudinary từ chối file={}: {}", originalFilename, e.getMessage());
            throw new InvalidUploadException("File không đọc được hoặc không đúng định dạng ảnh/PDF");
        }
        return new StoredFile(
                (String) result.get("secure_url"),
                (String) result.get("public_id"),
                ((Number) result.get("bytes")).longValue(),
                (String) result.get("format"));
    }
}
