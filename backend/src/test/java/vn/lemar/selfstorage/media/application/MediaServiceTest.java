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

    private final FileStorage fileStorage = mock(FileStorage.class);
    private final MediaService service = new MediaService(fileStorage);

    @Test
    void delegatesValidFileToStorage() {
        FileStorage.StoredFile stored = new FileStorage.StoredFile("https://x/y.png", "y", 3, "png");
        when(fileStorage.store(eq(CONTENT), eq("anh.png"), eq("image/png"))).thenReturn(stored);

        assertThat(service.upload(CONTENT, "anh.png", "image/png")).isEqualTo(stored);
    }

    @Test
    void acceptsUppercaseContentType() {
        when(fileStorage.store(any(), any(), any()))
                .thenReturn(new FileStorage.StoredFile("u", "p", 3, "png"));

        service.upload(CONTENT, "anh.PNG", "IMAGE/PNG");

        verify(fileStorage).store(CONTENT, "anh.PNG", "IMAGE/PNG");
    }

    @Test
    void rejectsEmptyFile() {
        assertThatThrownBy(() -> service.upload(new byte[0], "a.png", "image/png"))
                .isInstanceOf(InvalidUploadException.class)
                .hasMessage("File rỗng");
        assertThatThrownBy(() -> service.upload(null, "a.png", "image/png"))
                .isInstanceOf(InvalidUploadException.class);
        verifyNoInteractions(fileStorage);
    }

    @Test
    void rejectsUnsupportedType() {
        assertThatThrownBy(() -> service.upload(CONTENT, "a.exe", "application/x-msdownload"))
                .isInstanceOf(InvalidUploadException.class);
        assertThatThrownBy(() -> service.upload(CONTENT, "a.png", null))
                .isInstanceOf(InvalidUploadException.class);
        verifyNoInteractions(fileStorage);
    }

    @Test
    void rejectsOversizeFile() {
        byte[] tooBig = new byte[(int) MediaService.MAX_SIZE_BYTES + 1];

        assertThatThrownBy(() -> service.upload(tooBig, "a.png", "image/png"))
                .isInstanceOf(InvalidUploadException.class)
                .hasMessage(MediaService.MAX_SIZE_MESSAGE);
        verifyNoInteractions(fileStorage);
    }

    @Test
    void acceptsExactlyMaxSize() {
        byte[] max = new byte[(int) MediaService.MAX_SIZE_BYTES];
        when(fileStorage.store(any(), any(), any()))
                .thenReturn(new FileStorage.StoredFile("u", "p", max.length, "png"));

        service.upload(max, "a.png", "image/png");

        verify(fileStorage).store(max, "a.png", "image/png");
    }
}
