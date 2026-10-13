package vn.lemar.selfstorage.media.application;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import java.io.IOException;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import vn.lemar.selfstorage.media.FileStorage;
import vn.lemar.selfstorage.media.application.exception.MediaStorageException;

/** Impl Cloudinary — {@code resource_type=auto} để nhận cả ảnh lẫn PDF. */
@Component
public class CloudinaryFileStorage implements FileStorage {

    private static final Logger LOG = LoggerFactory.getLogger(CloudinaryFileStorage.class);

    private final Cloudinary cloudinary;

    public CloudinaryFileStorage(Cloudinary cloudinary) {
        this.cloudinary = cloudinary;
    }

    @Override
    public StoredFile store(byte[] content, String originalFilename, String contentType) {
        try {
            Map<?, ?> result = cloudinary.uploader().upload(content,
                    ObjectUtils.asMap("resource_type", "auto"));
            return new StoredFile(
                    (String) result.get("secure_url"),
                    (String) result.get("public_id"),
                    ((Number) result.get("bytes")).longValue(),
                    (String) result.get("format"));
        } catch (IOException e) {
            LOG.error("Upload Cloudinary thất bại, file={}", originalFilename, e);
            throw new MediaStorageException("Không tải được file lên storage", e);
        }
    }
}
