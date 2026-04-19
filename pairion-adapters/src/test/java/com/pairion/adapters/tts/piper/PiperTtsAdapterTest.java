package com.pairion.adapters.tts.piper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.pairion.core.tts.TtsCapabilities;
import com.pairion.core.tts.TtsEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Tests for {@link PiperTtsAdapter}. */
class PiperTtsAdapterTest {

    private PiperTtsNative mockNative;
    private PiperTtsAdapter adapter;

    @BeforeEach
    void setUp() {
        mockNative = mock(PiperTtsNative.class);
        when(mockNative.isAvailable()).thenReturn(true);
        when(mockNative.getSampleRate()).thenReturn(22050);
        adapter = new PiperTtsAdapter(mockNative);
    }

    @Test
    void nameReturnsPiper() {
        assertThat(adapter.name()).isEqualTo("piper");
    }

    @Test
    void capabilitiesAvailableWhenNativeAvailable() {
        TtsCapabilities caps = adapter.capabilities();
        assertThat(caps.available()).isTrue();
        assertThat(caps.supportsStreaming()).isTrue();
    }

    @Test
    void capabilitiesUnavailableWhenNativeUnavailable() {
        when(mockNative.isAvailable()).thenReturn(false);
        TtsCapabilities caps = adapter.capabilities();
        assertThat(caps.available()).isFalse();
    }

    @Test
    @SuppressWarnings("unchecked")
    void speakProducesOpusChunksAndCompletion() {
        // Simulate Piper returning 22050 Hz PCM (one 20ms frame = 441 samples = 882 bytes)
        byte[] pcm22050 = new byte[882 * 4]; // 4 frames of 22050 Hz PCM
        doAnswer(
                        inv -> {
                            Consumer<byte[]> consumer = inv.getArgument(1);
                            consumer.accept(pcm22050);
                            return null;
                        })
                .when(mockNative)
                .synthesize(any(), any());

        List<TtsEvent> events = new ArrayList<>();
        adapter.speak("Hello world", events::add);

        long chunkCount = events.stream().filter(e -> e instanceof TtsEvent.Chunk).count();
        long completedCount = events.stream().filter(e -> e instanceof TtsEvent.Completed).count();

        assertThat(chunkCount).isGreaterThan(0);
        assertThat(completedCount).isEqualTo(1);

        // Verify chunks are marked as Opus
        events.stream()
                .filter(e -> e instanceof TtsEvent.Chunk)
                .map(e -> (TtsEvent.Chunk) e)
                .forEach(chunk -> assertThat(chunk.isOpus()).isTrue());
    }

    @Test
    void speakWhenUnavailableEmitsCompletionOnly() {
        when(mockNative.isAvailable()).thenReturn(false);
        List<TtsEvent> events = new ArrayList<>();
        adapter.speak("Hello", events::add);

        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOf(TtsEvent.Completed.class);
    }

    @Test
    void resampleNoOpWhenSameRate() {
        byte[] pcm = {0x01, 0x02, 0x03, 0x04};
        byte[] result = PiperTtsAdapter.resample(pcm, 16000, 16000);
        assertThat(result).isEqualTo(pcm);
    }

    @Test
    void resampleHandlesLastSampleBoundary() {
        // 4 bytes = 2 samples at 22050 Hz. For i=1 (last output): srcIdx=1, srcIdx+1=2 >= 2
        // → triggers the s1 = s0 boundary path (no next sample to interpolate)
        byte[] pcm = new byte[4];
        byte[] result = PiperTtsAdapter.resample(pcm, 22050, 16000);
        assertThat(result).isNotEmpty();
    }

    @Test
    void resampleDownsamplesCorrectly() {
        // 22050 Hz silence → 16000 Hz
        byte[] silence22050 = new byte[882]; // 441 samples at 22050 Hz
        byte[] result = PiperTtsAdapter.resample(silence22050, 22050, 16000);
        // Expected: ceil(441 * 16000 / 22050) = ceil(319.6) = 320 samples = 640 bytes
        assertThat(result).hasSize(640);
    }

    @Test
    @SuppressWarnings("unchecked")
    void speakPadsLastFrameWhenPcmNotAligned() {
        // 16 kHz native rate, PCM that is 1.5 frames (960 bytes = 480 samples)
        // → first iteration takes full 640-byte frame, second takes padding path
        when(mockNative.getSampleRate()).thenReturn(16000);
        PiperTtsAdapter adapter16k = new PiperTtsAdapter(mockNative);

        byte[] pcm = new byte[960]; // 480 samples — not a multiple of 320
        doAnswer(
                        inv -> {
                            Consumer<byte[]> consumer = inv.getArgument(1);
                            consumer.accept(pcm);
                            return null;
                        })
                .when(mockNative)
                .synthesize(any(), any());

        List<TtsEvent> events = new ArrayList<>();
        adapter16k.speak("hi", events::add);

        long chunkCount = events.stream().filter(e -> e instanceof TtsEvent.Chunk).count();
        // 960 bytes → 1 full frame (640) + 1 padded frame (320 bytes padded to 640)
        assertThat(chunkCount).isEqualTo(2);
    }

    @Test
    void speakAtNativeSampleRateSkipsResample() {
        // If Piper returns 16000 Hz, no resample should happen
        when(mockNative.getSampleRate()).thenReturn(16000);
        PiperTtsAdapter adapterAt16k = new PiperTtsAdapter(mockNative);

        // 16 kHz PCM: exactly one 20ms frame = 320 samples = 640 bytes
        byte[] pcm16k = new byte[640];
        doAnswer(
                        inv -> {
                            Consumer<byte[]> consumer = inv.getArgument(1);
                            consumer.accept(pcm16k);
                            return null;
                        })
                .when(mockNative)
                .synthesize(any(), any());

        List<TtsEvent> events = new ArrayList<>();
        adapterAt16k.speak("Hi", events::add);

        assertThat(events).anyMatch(e -> e instanceof TtsEvent.Chunk);
        assertThat(events).anyMatch(e -> e instanceof TtsEvent.Completed);
    }
}
