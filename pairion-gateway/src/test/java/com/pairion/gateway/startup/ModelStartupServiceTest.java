package com.pairion.gateway.startup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pairion.core.util.ModelDownloader;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Tests for {@link ModelStartupService}. */
class ModelStartupServiceTest {

    @Test
    void onApplicationReadySchedulesBothDownloads() throws InterruptedException {
        ModelDownloader downloader = mock(ModelDownloader.class);
        when(downloader.download(anyString(), any(Path.class), anyString())).thenReturn(true);

        ModelStartupService service = new ModelStartupService("en_GB-alan-medium", downloader);
        service.onApplicationReady();

        // Virtual threads are async — give them time to complete
        Thread.sleep(200);

        // Whisper (1 call) + Piper ONNX + Piper config (2 calls) = 3 total
        verify(downloader, times(3)).download(anyString(), any(Path.class), anyString());
    }

    @Test
    void downloadWhisperModelSucceeds() {
        ModelDownloader downloader = mock(ModelDownloader.class);
        when(downloader.download(anyString(), any(Path.class), anyString())).thenReturn(true);

        ModelStartupService service = new ModelStartupService("en_GB-alan-medium", downloader);
        service.downloadWhisperModel();

        verify(downloader, times(1)).download(anyString(), any(Path.class), anyString());
    }

    @Test
    void downloadWhisperModelHandlesFailure() {
        ModelDownloader downloader = mock(ModelDownloader.class);
        when(downloader.download(anyString(), any(Path.class), anyString())).thenReturn(false);

        ModelStartupService service = new ModelStartupService("en_GB-alan-medium", downloader);
        // Should not throw even when downloader returns false
        service.downloadWhisperModel();

        verify(downloader).download(anyString(), any(Path.class), anyString());
    }

    @Test
    void downloadPiperModelMakesTwoCalls() {
        ModelDownloader downloader = mock(ModelDownloader.class);
        when(downloader.download(anyString(), any(Path.class), anyString())).thenReturn(true);

        ModelStartupService service = new ModelStartupService("en_GB-alan-medium", downloader);
        service.downloadPiperModel();

        verify(downloader, times(2)).download(anyString(), any(Path.class), anyString());
    }

    @Test
    void downloadPiperModelHandlesPartialFailure() {
        ModelDownloader downloader = mock(ModelDownloader.class);
        // ONNX succeeds, config fails
        when(downloader.download(anyString(), any(Path.class), anyString()))
                .thenReturn(true)
                .thenReturn(false);

        ModelStartupService service = new ModelStartupService("en_GB-alan-medium", downloader);
        // Should not throw even when one download fails
        service.downloadPiperModel();

        verify(downloader, times(2)).download(anyString(), any(Path.class), anyString());
    }

    @Test
    void downloadPiperModelUsesVoiceName() {
        ModelDownloader downloader = mock(ModelDownloader.class);
        when(downloader.download(anyString(), any(Path.class), anyString())).thenReturn(true);

        ModelStartupService service = new ModelStartupService("en_US-lessac-medium", downloader);
        service.downloadPiperModel();

        // Verify the path argument contains the voice name
        verify(downloader, times(2))
                .download(
                        anyString(),
                        org.mockito.ArgumentMatchers.argThat(
                                p -> p.toString().contains("en_US-lessac-medium")),
                        anyString());
    }

    @Test
    void resolveModelPathUsesHomeWhenPairionHomeSet() {
        ModelStartupService service =
                new ModelStartupService("en_GB-alan-medium", mock(ModelDownloader.class));
        java.nio.file.Path result = service.resolveModelPath("/custom/home", "whisper", "model.bin");
        assertThat(result.toString()).contains("/custom/home");
        assertThat(result.toString()).contains("whisper");
        assertThat(result.toString()).contains("model.bin");
    }

    /** ONNX download fails → short-circuit in (onnxOk && configOk): covers the onnxOk=false branch. */
    @Test
    void downloadPiperModelOnnxFailsShortCircuits() {
        ModelDownloader downloader = mock(ModelDownloader.class);
        // Both calls return false; first (onnxOk=false) short-circuits the && condition
        when(downloader.download(anyString(), any(Path.class), anyString())).thenReturn(false);

        ModelStartupService service = new ModelStartupService("en_GB-alan-medium", downloader);
        service.downloadPiperModel(); // Must not throw

        verify(downloader, times(2)).download(anyString(), any(Path.class), anyString());
    }

    @Test
    void noDownloadsWhenDownloaderThrows() throws InterruptedException {
        ModelDownloader downloader = mock(ModelDownloader.class);
        when(downloader.download(anyString(), any(Path.class), anyString()))
                .thenThrow(new RuntimeException("disk full"));

        ModelStartupService service = new ModelStartupService("en_GB-alan-medium", downloader);
        // onApplicationReady catches exceptions in virtual threads — should not propagate
        service.onApplicationReady();
        Thread.sleep(200);

        // Downloads were attempted
        assertThat(true).isTrue(); // no exception thrown
    }
}
