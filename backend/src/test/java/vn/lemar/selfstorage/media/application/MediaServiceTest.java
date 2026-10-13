package vn.lemar.selfstorage.media.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import vn.lemar.selfstorage.media.FileStorage;
import vn.lemar.selfstorage.media.application.exception.InvalidUploadException;

class MediaServiceTest {

    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1, 2};
    private static final byte[] WEBP = {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P', 1};
    private static final byte[] PDF = {'%', 'P', 'D', 'F', '-', '1', '.', '4'};
    private static final long ACCOUNT_ID = 7L;

    private final FileStorage fileStorage = mock(FileStorage.class);
    private final MediaService service = new MediaService(fileStorage);

    private void stubStore() {
        when(fileStorage.store(any(), any(), any(), any()))
                .thenReturn(new FileStorage.StoredFile("u", "p", 1, "png"));
    }

    @Test
    void delegatesValidFileToStorageWithAccountFolder() {
        FileStorage.StoredFile stored = new FileStorage.StoredFile("https://x/y.png", "y", 11, "png");
        when(fileStorage.store(eq(PNG), eq("anh.png"), eq("image/png"), eq("uploads/acc-7")))
                .thenReturn(stored);

        assertThat(service.upload(PNG, "anh.png", "image/png", ACCOUNT_ID)).isEqualTo(stored);
    }

    @Test
    void acceptsAllAllowedTypes() {
        stubStore();

        service.upload(PNG, "a.png", "image/png", ACCOUNT_ID);
        service.upload(JPEG, "a.jpg", "image/jpeg", ACCOUNT_ID);
        service.upload(WEBP, "a.webp", "image/webp", ACCOUNT_ID);
        service.upload(PDF, "a.pdf", "application/pdf", ACCOUNT_ID);

        verify(fileStorage, times(4)).store(any(), any(), any(), eq("uploads/acc-7"));
    }

    @Test
    void acceptsUppercaseContentType() {
        stubStore();

        service.upload(PNG, "anh.PNG", "IMAGE/PNG", ACCOUNT_ID);

        verify(fileStorage).store(PNG, "anh.PNG", "IMAGE/PNG", "uploads/acc-7");
    }

    @Test
    void acceptsContentTypeWithParameters() {
        stubStore();

        service.upload(PNG, "anh.png", "image/png; charset=binary", ACCOUNT_ID);

        verify(fileStorage).store(PNG, "anh.png", "image/png; charset=binary", "uploads/acc-7");
    }

    @Test
    void acceptsNonStandardJpgContentType() {
        stubStore();

        service.upload(JPEG, "anh.jpg", "image/jpg", ACCOUNT_ID);

        verify(fileStorage).store(JPEG, "anh.jpg", "image/jpg", "uploads/acc-7");
    }

    @Test
    void rejectsEmptyFile() {
        assertThatThrownBy(() -> service.upload(new byte[0], "a.png", "image/png", ACCOUNT_ID))
                .isInstanceOf(InvalidUploadException.class)
                .hasMessage("File rỗng");
        assertThatThrownBy(() -> service.upload(null, "a.png", "image/png", ACCOUNT_ID))
                .isInstanceOf(InvalidUploadException.class);
        verifyNoInteractions(fileStorage);
    }

    @Test
    void rejectsUnsupportedType() {
        assertThatThrownBy(() -> service.upload(PNG, "a.exe", "application/x-msdownload", ACCOUNT_ID))
                .isInstanceOf(InvalidUploadException.class);
        assertThatThrownBy(() -> service.upload(PNG, "a.png", null, ACCOUNT_ID))
                .isInstanceOf(InvalidUploadException.class);
        verifyNoInteractions(fileStorage);
    }

    @Test
    void rejectsContentNotMatchingDeclaredType() {
        assertThatThrownBy(() -> service.upload(PNG, "a.pdf", "application/pdf", ACCOUNT_ID))
                .isInstanceOf(InvalidUploadException.class)
                .hasMessage("Nội dung file không khớp với định dạng khai báo");
        verifyNoInteractions(fileStorage);
    }

    @Test
    void rejectsUndetectableContent() {
        byte[] garbage = "this is not an image at all, just text".getBytes();

        assertThatThrownBy(() -> service.upload(garbage, "a.png", "image/png", ACCOUNT_ID))
                .isInstanceOf(InvalidUploadException.class)
                .hasMessage("File không đọc được (không nhận diện được định dạng)");
        verifyNoInteractions(fileStorage);
    }

    @Test
    void rejectsOversizeFile() {
        byte[] tooBig = new byte[(int) MediaService.MAX_SIZE_BYTES + 1];
        System.arraycopy(PNG, 0, tooBig, 0, PNG.length);

        assertThatThrownBy(() -> service.upload(tooBig, "a.png", "image/png", ACCOUNT_ID))
                .isInstanceOf(InvalidUploadException.class)
                .hasMessage(MediaService.MAX_SIZE_MESSAGE);
        verifyNoInteractions(fileStorage);
    }

    @Test
    void acceptsExactlyMaxSize() {
        byte[] max = new byte[(int) MediaService.MAX_SIZE_BYTES];
        System.arraycopy(PNG, 0, max, 0, PNG.length);
        stubStore();

        service.upload(max, "a.png", "image/png", ACCOUNT_ID);

        verify(fileStorage).store(max, "a.png", "image/png", "uploads/acc-7");
    }
}
