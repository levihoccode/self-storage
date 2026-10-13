package vn.lemar.selfstorage.media.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import vn.lemar.selfstorage.media.FileStorage;
import vn.lemar.selfstorage.media.application.exception.InvalidUploadException;

class MediaServiceTest {

    private static final byte[] CONTENT = {1, 2, 3};
    private static final long ACCOUNT_ID = 7L;

    private final FileStorage fileStorage = mock(FileStorage.class);
    private final MediaService service = new MediaService(fileStorage);

    @Test
    void delegatesValidFileToStorageWithAccountFolder() {
        FileStorage.StoredFile stored = new FileStorage.StoredFile("https://x/y.png", "y", 3, "png");
        when(fileStorage.store(eq(CONTENT), eq("anh.png"), eq("image/png"), eq("uploads/acc-7")))
                .thenReturn(stored);

        assertThat(service.upload(CONTENT, "anh.png", "image/png", ACCOUNT_ID)).isEqualTo(stored);
    }

    @Test
    void acceptsUppercaseContentType() {
        when(fileStorage.store(any(), any(), any(), any()))
                .thenReturn(new FileStorage.StoredFile("u", "p", 3, "png"));

        service.upload(CONTENT, "anh.PNG", "IMAGE/PNG", ACCOUNT_ID);

        verify(fileStorage).store(CONTENT, "anh.PNG", "IMAGE/PNG", "uploads/acc-7");
    }

    @Test
    void acceptsContentTypeWithParameters() {
        when(fileStorage.store(any(), any(), any(), any()))
                .thenReturn(new FileStorage.StoredFile("u", "p", 3, "png"));

        service.upload(CONTENT, "anh.png", "image/png; charset=binary", ACCOUNT_ID);

        verify(fileStorage).store(CONTENT, "anh.png", "image/png; charset=binary", "uploads/acc-7");
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
        assertThatThrownBy(() -> service.upload(CONTENT, "a.exe", "application/x-msdownload", ACCOUNT_ID))
                .isInstanceOf(InvalidUploadException.class);
        assertThatThrownBy(() -> service.upload(CONTENT, "a.png", null, ACCOUNT_ID))
                .isInstanceOf(InvalidUploadException.class);
        verifyNoInteractions(fileStorage);
    }

    @Test
    void rejectsOversizeFile() {
        byte[] tooBig = new byte[(int) MediaService.MAX_SIZE_BYTES + 1];

        assertThatThrownBy(() -> service.upload(tooBig, "a.png", "image/png", ACCOUNT_ID))
                .isInstanceOf(InvalidUploadException.class)
                .hasMessage(MediaService.MAX_SIZE_MESSAGE);
        verifyNoInteractions(fileStorage);
    }

    @Test
    void acceptsExactlyMaxSize() {
        byte[] max = new byte[(int) MediaService.MAX_SIZE_BYTES];
        when(fileStorage.store(any(), any(), any(), any()))
                .thenReturn(new FileStorage.StoredFile("u", "p", max.length, "png"));

        service.upload(max, "a.png", "image/png", ACCOUNT_ID);

        verify(fileStorage).store(max, "a.png", "image/png", "uploads/acc-7");
    }
}
