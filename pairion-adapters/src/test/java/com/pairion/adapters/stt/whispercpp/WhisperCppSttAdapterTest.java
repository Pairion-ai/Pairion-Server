package com.pairion.adapters.stt.whispercpp;

import static org.assertj.core.api.Assertions.assertThat;

import com.pairion.adapters.stt.spi.SttAdapter;
import com.pairion.core.stt.SttCapabilities;
import com.pairion.core.stt.SttEvent;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Tests for {@link WhisperCppSttAdapter} with mocked native implementation. */
class WhisperCppSttAdapterTest {

    private WhisperCppNative mockNative(boolean available, String transcript) {
        return new WhisperCppNative() {
            @Override
            public boolean isAvailable() {
                return available;
            }

            @Override
            public String transcribe(float[] samples) {
                return transcript;
            }

            @Override
            public String expectedLibraryPath() {
                return "/mock/path/libwhisper.dylib";
            }

            @Override
            public String expectedModelPath() {
                return "/mock/path/ggml-small.en.bin";
            }
        };
    }

    @Test
    void nameReturnsWhispercpp() {
        WhisperCppSttAdapter adapter = new WhisperCppSttAdapter(mockNative(true, ""));
        assertThat(adapter.name()).isEqualTo("whispercpp");
    }

    @Test
    void capabilitiesWhenAvailable() {
        WhisperCppSttAdapter adapter = new WhisperCppSttAdapter(mockNative(true, ""));
        SttCapabilities caps = adapter.capabilities();
        assertThat(caps.available()).isTrue();
        assertThat(caps.supportsStreaming()).isTrue();
    }

    @Test
    void capabilitiesWhenUnavailable() {
        WhisperCppSttAdapter adapter = new WhisperCppSttAdapter(mockNative(false, ""));
        SttCapabilities caps = adapter.capabilities();
        assertThat(caps.available()).isFalse();
    }

    @Test
    void sessionFinalizeEmitsFinalTranscript() {
        WhisperCppSttAdapter adapter = new WhisperCppSttAdapter(mockNative(true, "hello world"));
        List<SttEvent> events = new ArrayList<>();
        SttAdapter.SttSession session = adapter.createSession(events::add);

        byte[] pcm = makePcm(320);
        session.feedAudio(pcm);
        session.finalizeStream();

        assertThat(events).isNotEmpty();
        SttEvent lastEvent = events.get(events.size() - 1);
        assertThat(lastEvent).isInstanceOf(SttEvent.Final.class);
        assertThat(((SttEvent.Final) lastEvent).text()).isEqualTo("hello world");
    }

    @Test
    void sessionUnavailableEmitsEmptyFinal() {
        WhisperCppSttAdapter adapter = new WhisperCppSttAdapter(mockNative(false, ""));
        List<SttEvent> events = new ArrayList<>();
        SttAdapter.SttSession session = adapter.createSession(events::add);

        session.feedAudio(makePcm(320));
        session.finalizeStream();

        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOf(SttEvent.Final.class);
        assertThat(((SttEvent.Final) events.get(0)).text()).isEmpty();
    }

    @Test
    void sessionFeedAudioWithinThrottleDoesNotEmitPartial() {
        WhisperCppSttAdapter adapter = new WhisperCppSttAdapter(mockNative(true, "partial text"));
        List<SttEvent> events = new ArrayList<>();
        SttAdapter.SttSession session = adapter.createSession(events::add);

        // First feed — within throttle window
        session.feedAudio(makePcm(320));
        // Immediately feed again — should not trigger partial (throttle not elapsed)
        session.feedAudio(makePcm(320));

        boolean hasPartial = events.stream().anyMatch(e -> e instanceof SttEvent.Partial);
        assertThat(hasPartial).isFalse();
    }

    @Test
    void sessionFeedAudioThrottleElapsedButUnavailable() throws InterruptedException {
        WhisperCppSttAdapter adapter = new WhisperCppSttAdapter(mockNative(false, ""));
        List<SttEvent> events = new ArrayList<>();
        SttAdapter.SttSession session = adapter.createSession(events::add);

        session.feedAudio(makePcm(320));
        Thread.sleep(250);
        session.feedAudio(makePcm(320));

        boolean hasPartial = events.stream().anyMatch(e -> e instanceof SttEvent.Partial);
        assertThat(hasPartial).isFalse();
    }

    @Test
    void sessionEmitsPartialWithThrottle() throws InterruptedException {
        WhisperCppSttAdapter adapter = new WhisperCppSttAdapter(mockNative(true, "partial text"));
        List<SttEvent> events = new ArrayList<>();
        SttAdapter.SttSession session = adapter.createSession(events::add);

        session.feedAudio(makePcm(320));
        Thread.sleep(250);
        session.feedAudio(makePcm(320));

        boolean hasPartial = events.stream().anyMatch(e -> e instanceof SttEvent.Partial);
        assertThat(hasPartial).isTrue();
    }

    @Test
    void sessionBlankPartialNotEmitted() throws InterruptedException {
        WhisperCppSttAdapter adapter = new WhisperCppSttAdapter(mockNative(true, "   "));
        List<SttEvent> events = new ArrayList<>();
        SttAdapter.SttSession session = adapter.createSession(events::add);

        session.feedAudio(makePcm(320));
        Thread.sleep(250);
        session.feedAudio(makePcm(320));

        boolean hasPartial = events.stream().anyMatch(e -> e instanceof SttEvent.Partial);
        assertThat(hasPartial).isFalse();
    }

    @Test
    void pcmToFloatConversion() {
        byte[] pcm = new byte[4];
        ByteBuffer buf = ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN);
        buf.putShort((short) 16384);
        buf.putShort((short) -16384);

        float[] samples = WhisperCppSttAdapter.WhisperSttSession.pcmToFloat(pcm);
        assertThat(samples).hasSize(2);
        assertThat(samples[0]).isCloseTo(0.5f, org.assertj.core.data.Offset.offset(0.001f));
        assertThat(samples[1]).isCloseTo(-0.5f, org.assertj.core.data.Offset.offset(0.001f));
    }

    private byte[] makePcm(int sampleCount) {
        byte[] pcm = new byte[sampleCount * 2];
        ByteBuffer buf = ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < sampleCount; i++) {
            buf.putShort((short) (i % 100));
        }
        return pcm;
    }
}
