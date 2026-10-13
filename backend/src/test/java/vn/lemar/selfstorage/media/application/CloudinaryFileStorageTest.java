package vn.lemar.selfstorage.media.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;
import java.io.IOException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import vn.lemar.selfstorage.media.FileStorage;
import vn.lemar.selfstorage.media.application.exception.MediaStorageException;

class CloudinaryFileStorageTest {

    private final Cloudinary cloudinary = mock(Cloudinary.class);
    private final Uploader uploader = mock(Uploader.class);
    private final CloudinaryFileStorage storage = new CloudinaryFileStorage(cloudinary);

    @Test
    void mapsCloudinaryResultToStoredFile() throws IOException {
        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.upload(any(byte[].class), anyMap())).thenReturn(Map.of(
                "secure_url", "https://res.cloudinary.com/demo/image/upload/v1/anh.png",
                "public_id", "anh",
                "bytes", 20480,
                "format", "png"));

        FileStorage.StoredFile stored = storage.store(new byte[]{1}, "anh.png", "image/png");

        assertThat(stored.url()).isEqualTo("https://res.cloudinary.com/demo/image/upload/v1/anh.png");
        assertThat(stored.publicId()).isEqualTo("anh");
        assertThat(stored.bytes()).isEqualTo(20480);
        assertThat(stored.format()).isEqualTo("png");
    }

    @Test
    void wrapsIOExceptionAsStorageException() throws IOException {
        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.upload(any(byte[].class), anyMap())).thenThrow(new IOException("boom"));

        assertThatThrownBy(() -> storage.store(new byte[]{1}, "anh.png", "image/png"))
                .isInstanceOf(MediaStorageException.class)
                .hasCauseInstanceOf(IOException.class);
    }
}
