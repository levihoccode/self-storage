package vn.lemar.selfstorage.media.application;

import java.util.Locale;
import org.springframework.stereotype.Service;
import vn.lemar.selfstorage.media.FileStorage;
import vn.lemar.selfstorage.media.application.exception.InvalidUploadException;
import vn.lemar.selfstorage.media.domain.MediaFileType;

/**
 * Validate file theo contract (#134) rồi chuyển cho {@link FileStorage}.
 *
 * <p>Hai lớp kiểm loại file: content-type client khai phải nằm trong allowlist, và nội dung
 * phải khớp loại đó theo chữ ký (magic bytes) — chặn file khai sai định dạng trước khi upload.
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

    private final FileStorage fileStorage;

    public MediaService(FileStorage fileStorage) {
        this.fileStorage = fileStorage;
    }

    public FileStorage.StoredFile upload(byte[] content, String originalFilename, String contentType,
                                         long accountId) {
        if (content == null || content.length == 0) {
            throw new InvalidUploadException("File rỗng");
        }
        MediaFileType declared = declaredType(contentType);
        if (content.length > MAX_SIZE_BYTES) {
            throw new InvalidUploadException(MAX_SIZE_MESSAGE);
        }
        MediaFileType actual = MediaFileType.detect(content)
                .orElseThrow(() -> new InvalidUploadException(
                        "File không đọc được (không nhận diện được định dạng)"));
        if (actual != declared) {
            throw new InvalidUploadException("Nội dung file không khớp với định dạng khai báo");
        }
        return fileStorage.store(content, originalFilename, contentType, FOLDER_PREFIX + accountId);
    }

    /** Header có thể kèm tham số (`image/png; charset=binary`); `image/jpg` map về JPEG. */
    private MediaFileType declaredType(String contentType) {
        String mediaType = contentType == null ? "" : contentType.split(";")[0].trim().toLowerCase(Locale.ROOT);
        return MediaFileType.fromContentType(mediaType)
                .orElseThrow(() -> new InvalidUploadException(
                        "Định dạng file không được hỗ trợ (chỉ nhận JPEG, PNG, WebP, PDF)"));
    }
}
