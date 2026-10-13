package vn.lemar.selfstorage.media.domain;

import java.util.List;
import java.util.Optional;

/**
 * Loại file được phép upload (JPEG/PNG/WebP/PDF) — hai vai trò:
 *
 * <ul>
 *   <li>map content-type client khai (đã chuẩn hoá: bỏ tham số, lowercase) → loại;</li>
 *   <li>nhận diện loại thật từ chữ ký nội dung (magic bytes) — dùng để chặn file khai sai
 *       định dạng (khai PDF nhưng nội dung là ảnh).</li>
 * </ul>
 */
public enum MediaFileType {

    JPEG("image/jpeg", "image/jpg"),
    PNG("image/png"),
    WEBP("image/webp"),
    PDF("application/pdf");

    private static final byte[] PNG_SIGNATURE =
            {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] JPEG_SIGNATURE = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final int PDF_HEADER_SCAN_LIMIT = 1024;

    private final List<String> contentTypes;

    MediaFileType(String... contentTypes) {
        this.contentTypes = List.of(contentTypes);
    }

    /** @param mediaType content-type đã bỏ tham số + lowercase */
    public static Optional<MediaFileType> fromContentType(String mediaType) {
        for (MediaFileType type : values()) {
            if (type.contentTypes.contains(mediaType)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }

    /** Nhận diện theo chữ ký nội dung; empty nếu không khớp loại nào được phép. */
    public static Optional<MediaFileType> detect(byte[] content) {
        if (startsWith(content, PNG_SIGNATURE)) {
            return Optional.of(PNG);
        }
        if (startsWith(content, JPEG_SIGNATURE)) {
            return Optional.of(JPEG);
        }
        if (isWebp(content)) {
            return Optional.of(WEBP);
        }
        if (hasPdfHeader(content)) {
            return Optional.of(PDF);
        }
        return Optional.empty();
    }

    private static boolean startsWith(byte[] content, byte[] signature) {
        if (content.length < signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if (content[i] != signature[i]) {
                return false;
            }
        }
        return true;
    }

    private static boolean isWebp(byte[] content) {
        return content.length >= 12
                && content[0] == 'R' && content[1] == 'I' && content[2] == 'F' && content[3] == 'F'
                && content[8] == 'W' && content[9] == 'E' && content[10] == 'B' && content[11] == 'P';
    }

    /** PDF spec cho phép header nằm trong 1024 byte đầu (một số file có BOM/junk trước). */
    private static boolean hasPdfHeader(byte[] content) {
        int lastStart = Math.min(content.length - 4, PDF_HEADER_SCAN_LIMIT);
        for (int i = 0; i <= lastStart; i++) {
            if (content[i] == '%' && content[i + 1] == 'P'
                    && content[i + 2] == 'D' && content[i + 3] == 'F') {
                return true;
            }
        }
        return false;
    }
}
