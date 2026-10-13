package vn.lemar.selfstorage.media.application;

import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;
import vn.lemar.selfstorage.media.FileStorage;
import vn.lemar.selfstorage.media.application.exception.InvalidUploadException;

/**
 * Validate file theo contract (#134) rồi chuyển cho {@link FileStorage}.
 *
 * <p>Giới hạn 10MB khớp cap upload của Cloudinary free plan; servlet multipart đặt 12MB
 * (`application.yml`) để file tới được đây và lỗi kích thước có message 400 riêng.
 *
 * <p>Mỗi account có folder riêng (`uploads/acc-{id}`) — file mồ côi khi flow không hoàn tất
 * vẫn truy vết được chủ sở hữu trong console storage.
 */
@Service
public class MediaService {

    public static final long MAX_SIZE_BYTES = 10L * 1024 * 1024;
    public static final String MAX_SIZE_MESSAGE = "File vượt quá kích thước cho phép (tối đa 10MB)";

    static final String FOLDER_PREFIX = "uploads/acc-";

    private static final Set<String> ALLOWED_CONTENT_TYPES =
            Set.of("image/jpeg", "image/png", "image/webp", "application/pdf");

    private final FileStorage fileStorage;

    public MediaService(FileStorage fileStorage) {
        this.fileStorage = fileStorage;
    }

    public FileStorage.StoredFile upload(byte[] content, String originalFilename, String contentType,
                                         long accountId) {
        if (content == null || content.length == 0) {
            throw new InvalidUploadException("File rỗng");
        }
        if (!isAllowedContentType(contentType)) {
            throw new InvalidUploadException("Định dạng file không được hỗ trợ (chỉ nhận JPEG, PNG, WebP, PDF)");
        }
        if (content.length > MAX_SIZE_BYTES) {
            throw new InvalidUploadException(MAX_SIZE_MESSAGE);
        }
        return fileStorage.store(content, originalFilename, contentType, FOLDER_PREFIX + accountId);
    }

    /** Header có thể kèm tham số (`image/png; charset=binary`) — chỉ so phần media type. */
    private boolean isAllowedContentType(String contentType) {
        if (contentType == null) {
            return false;
        }
        String mediaType = contentType.split(";")[0].trim().toLowerCase(Locale.ROOT);
        return ALLOWED_CONTENT_TYPES.contains(mediaType);
    }
}
