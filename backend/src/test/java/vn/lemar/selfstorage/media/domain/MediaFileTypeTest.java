package vn.lemar.selfstorage.media.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MediaFileTypeTest {

    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1};
    private static final byte[] WEBP = {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'};
    private static final byte[] PDF = {'%', 'P', 'D', 'F', '-', '1', '.', '4'};

    @Test
    void fromContentTypeMapsKnownValues() {
        assertThat(MediaFileType.fromContentType("image/jpeg")).contains(MediaFileType.JPEG);
        assertThat(MediaFileType.fromContentType("image/jpg")).contains(MediaFileType.JPEG);
        assertThat(MediaFileType.fromContentType("image/png")).contains(MediaFileType.PNG);
        assertThat(MediaFileType.fromContentType("image/webp")).contains(MediaFileType.WEBP);
        assertThat(MediaFileType.fromContentType("application/pdf")).contains(MediaFileType.PDF);
        assertThat(MediaFileType.fromContentType("image/gif")).isEmpty();
        assertThat(MediaFileType.fromContentType("")).isEmpty();
    }

    @Test
    void detectRecognizesEachSignature() {
        assertThat(MediaFileType.detect(PNG)).contains(MediaFileType.PNG);
        assertThat(MediaFileType.detect(JPEG)).contains(MediaFileType.JPEG);
        assertThat(MediaFileType.detect(WEBP)).contains(MediaFileType.WEBP);
        assertThat(MediaFileType.detect(PDF)).contains(MediaFileType.PDF);
    }

    @Test
    void detectAcceptsPdfHeaderWithinFirstKb() {
        byte[] withJunkPrefix = new byte[600];
        System.arraycopy(PDF, 0, withJunkPrefix, 500, PDF.length);

        assertThat(MediaFileType.detect(withJunkPrefix)).contains(MediaFileType.PDF);
    }

    @Test
    void detectRejectsUnknownContent() {
        assertThat(MediaFileType.detect("plain text".getBytes())).isEmpty();
        assertThat(MediaFileType.detect(new byte[0])).isEmpty();
        assertThat(MediaFileType.detect(new byte[]{(byte) 0xFF, (byte) 0xD8})).isEmpty();
    }

    @Test
    void detectRequiresWebpMarkerAfterRiff() {
        byte[] riffOnly = {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'A', 'V', 'I', ' '};

        assertThat(MediaFileType.detect(riffOnly)).isEmpty();
    }
}
