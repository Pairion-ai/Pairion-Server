package com.pairion.core.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests for {@link ModelDownloader}. */
@SuppressWarnings("unchecked")
class ModelDownloaderTest {

    @TempDir Path tempDir;

    @Test
    void downloadSucceedsWithCorrectHash() throws Exception {
        byte[] content = "model data".getBytes();
        String sha256 = sha256Hex(content);

        HttpClient mockClient = mockHttpClient(200, content, content.length);
        ModelDownloader downloader = new ModelDownloader(mockClient);

        Path target = tempDir.resolve("model.bin");
        boolean result = downloader.download("http://example.com/model.bin", target, sha256);

        assertThat(result).isTrue();
        assertThat(Files.exists(target)).isTrue();
        assertThat(Files.readAllBytes(target)).isEqualTo(content);
    }

    @Test
    void downloadSkipsIfAlreadyPresent() throws Exception {
        Path target = tempDir.resolve("model.bin");
        Files.writeString(target, "existing");

        ModelDownloader downloader = new ModelDownloader();
        boolean result = downloader.download("http://example.com/model.bin", target, "any");

        assertThat(result).isTrue();
    }

    @Test
    void downloadFailsOnHashMismatch() throws Exception {
        byte[] content = "model data".getBytes();

        HttpClient mockClient = mockHttpClient(200, content, content.length);
        ModelDownloader downloader = new ModelDownloader(mockClient);

        Path target = tempDir.resolve("model.bin");
        boolean result = downloader.download("http://example.com/model.bin", target, "badhash");

        assertThat(result).isFalse();
        assertThat(Files.exists(target)).isFalse();
    }

    @Test
    void downloadFailsOnHttpError() throws Exception {
        HttpClient mockClient = mockHttpClient(404, new byte[0], 0);
        ModelDownloader downloader = new ModelDownloader(mockClient);

        Path target = tempDir.resolve("model.bin");
        boolean result = downloader.download("http://example.com/model.bin", target, "hash");

        assertThat(result).isFalse();
    }

    @Test
    void downloadFailsOnIOException() throws Exception {
        HttpClient mockClient = mock(HttpClient.class);
        when(mockClient.send(any(HttpRequest.class), any())).thenThrow(new IOException("fail"));
        ModelDownloader downloader = new ModelDownloader(mockClient);

        Path target = tempDir.resolve("model.bin");
        boolean result = downloader.download("http://example.com/model.bin", target, "hash");

        assertThat(result).isFalse();
    }

    @Test
    void computeSha256Works() throws Exception {
        Path file = tempDir.resolve("test.bin");
        Files.write(file, "test data".getBytes());

        ModelDownloader downloader = new ModelDownloader();
        String hash = downloader.computeSha256(file);

        assertThat(hash).hasSize(64);
        assertThat(hash).isEqualTo(sha256Hex("test data".getBytes()));
    }

    @Test
    void writeWithProgressUnknownLength() throws Exception {
        byte[] data = "test data for progress".getBytes();
        Path target = tempDir.resolve("progress.bin");

        ModelDownloader downloader = new ModelDownloader();
        downloader.writeWithProgress(new ByteArrayInputStream(data), target, -1);

        assertThat(Files.readAllBytes(target)).isEqualTo(data);
    }

    @Test
    void writeWithProgressKnownLengthSmallChunks() throws Exception {
        byte[] data = new byte[200000];
        Path target = tempDir.resolve("progress.bin");

        // Feed in small chunks to trigger 10% boundary logging and non-boundary reads
        java.io.InputStream chunked =
                new java.io.InputStream() {
                    int pos = 0;

                    @Override
                    public int read() {
                        return pos < data.length ? data[pos++] & 0xFF : -1;
                    }

                    @Override
                    public int read(byte[] b, int off, int len) {
                        int toRead = Math.min(Math.min(len, 5000), data.length - pos);
                        if (toRead <= 0) {
                            return -1;
                        }
                        System.arraycopy(data, pos, b, off, toRead);
                        pos += toRead;
                        return toRead;
                    }
                };

        ModelDownloader downloader = new ModelDownloader();
        downloader.writeWithProgress(chunked, target, data.length);

        assertThat(Files.size(target)).isEqualTo(data.length);
    }

    @Test
    void getDigestThrowsOnBadAlgorithm() {
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> ModelDownloader.getDigest("NONEXISTENT-ALGORITHM"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not available");
    }

    @Test
    void getDigestWorksForSha256() {
        assertThat(ModelDownloader.getDigest("SHA-256")).isNotNull();
    }

    private HttpClient mockHttpClient(int status, byte[] body, long contentLength)
            throws Exception {
        HttpClient client = mock(HttpClient.class);
        HttpResponse<java.io.InputStream> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(new ByteArrayInputStream(body));

        HttpHeaders headers =
                HttpHeaders.of(
                        contentLength > 0
                                ? Map.of(
                                        "content-length",
                                        java.util.List.of(String.valueOf(contentLength)))
                                : Map.of(),
                        (a, b) -> true);
        when(response.headers()).thenReturn(headers);

        when(client.send(any(HttpRequest.class), any())).thenAnswer(inv -> response);
        return client;
    }

    private String sha256Hex(byte[] data) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(data));
    }
}
