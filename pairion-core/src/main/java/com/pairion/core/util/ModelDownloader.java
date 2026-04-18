package com.pairion.core.util;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Downloads model files with SHA-256 verification.
 *
 * <p>Used by STT and other adapter modules to download GGML model files on first run. Downloads
 * happen on a virtual thread so they do not block server startup.
 */
public class ModelDownloader {

    private static final Logger log = LoggerFactory.getLogger(ModelDownloader.class);

    private final HttpClient httpClient;

    /**
     * Constructs the downloader with the given HTTP client.
     *
     * @param httpClient the HTTP client for downloads
     */
    public ModelDownloader(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    /** Constructs the downloader with a default HTTP client. */
    public ModelDownloader() {
        this(HttpClient.newHttpClient());
    }

    /**
     * Downloads a file from the given URL to the target path, verifying its SHA-256 hash.
     *
     * <p>If the target file already exists, does nothing. If the hash does not match after
     * download, the file is deleted and an IOException is thrown.
     *
     * @param url the download URL
     * @param targetPath the local file path to save to
     * @param expectedSha256 the expected SHA-256 hex digest
     * @return true if the file was downloaded (or already existed), false on failure
     */
    public boolean download(String url, Path targetPath, String expectedSha256) {
        if (Files.exists(targetPath)) {
            log.info("Model already present at {}", targetPath);
            return true;
        }

        log.info("model.download.started: url={}, target={}", url, targetPath);
        long startMs = System.currentTimeMillis();

        try {
            Files.createDirectories(targetPath.getParent());

            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
            Path tempFile = targetPath.resolveSibling(targetPath.getFileName() + ".tmp");

            HttpResponse<InputStream> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() != 200) {
                log.error("model.download.failed: HTTP {}", response.statusCode());
                return false;
            }

            long contentLength = response.headers().firstValueAsLong("content-length").orElse(-1);
            try (InputStream in = response.body()) {
                writeWithProgress(in, tempFile, contentLength);
            }

            String actualSha = computeSha256(tempFile);
            long elapsedMs = System.currentTimeMillis() - startMs;
            long fileSize = Files.size(tempFile);

            if (!actualSha.equalsIgnoreCase(expectedSha256)) {
                log.error(
                        "model.download.hash_mismatch: expected={}, actual={}",
                        expectedSha256,
                        actualSha);
                Files.deleteIfExists(tempFile);
                return false;
            }

            Files.move(tempFile, targetPath, StandardCopyOption.REPLACE_EXISTING);
            log.info("model.download.complete: bytes={}, elapsed_ms={}", fileSize, elapsedMs);
            log.info("model.download.verified: sha256={}", actualSha);
            return true;
        } catch (IOException | InterruptedException e) {
            log.error("model.download.failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Writes an input stream to a file, logging progress at 10% intervals.
     *
     * @param in the input stream
     * @param target the target file
     * @param totalBytes expected total bytes (-1 if unknown)
     * @throws IOException if writing fails
     */
    void writeWithProgress(InputStream in, Path target, long totalBytes) throws IOException {
        byte[] buffer = new byte[65536];
        long written = 0;
        int lastPct = 0;
        try (var out = Files.newOutputStream(target)) {
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
                written += read;
                if (totalBytes > 0) {
                    int pct = (int) (written * 100 / totalBytes);
                    if (pct / 10 > lastPct / 10) {
                        log.info("model.download.progress={}%", pct);
                        lastPct = pct;
                    }
                }
            }
        }
    }

    /**
     * Computes the SHA-256 hex digest of a file.
     *
     * @param path the file to hash
     * @return lowercase hex SHA-256 digest
     * @throws IOException if reading fails
     */
    String computeSha256(Path path) throws IOException {
        // SHA-256 is guaranteed available per Java SE specification §9.3
        @SuppressWarnings("java:S4790")
        MessageDigest digest = getDigest("SHA-256");
        byte[] buffer = new byte[65536];
        try (var in = Files.newInputStream(path)) {
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    /**
     * Gets a MessageDigest instance, wrapping the checked exception.
     *
     * @param algorithm the digest algorithm name
     * @return the digest instance
     */
    static MessageDigest getDigest(String algorithm) {
        try {
            return MessageDigest.getInstance(algorithm);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(algorithm + " not available", e);
        }
    }
}
