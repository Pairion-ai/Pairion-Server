package com.pairion.agent.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import com.pairion.adapters.llm.spi.LlmAdapter;
import com.pairion.adapters.stt.spi.SttAdapter;
import com.pairion.agent.soul.SoulPromptProvider;
import com.pairion.core.agent.AgentState;
import com.pairion.core.llm.LlmEvent;
import com.pairion.core.stt.SttEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Tests for {@link AgentSession} turn loop with mocked STT and LLM. */
class AgentSessionTest {

    private SttAdapter sttAdapter;
    private LlmAdapter llmAdapter;
    private SoulPromptProvider soulProvider;
    private List<AgentSessionEvent> events;
    private AgentSession session;

    @BeforeEach
    void setUp() {
        sttAdapter = mock(SttAdapter.class);
        llmAdapter = mock(LlmAdapter.class);
        soulProvider = mock(SoulPromptProvider.class);
        when(soulProvider.getSystemPrompt("test-session")).thenReturn("You are Pairion.");
        events = new ArrayList<>();
        session =
                new AgentSession("test-session", sttAdapter, llmAdapter, soulProvider, events::add);
    }

    @Test
    void initialStateIsIdle() {
        assertThat(session.currentState()).isEqualTo(AgentState.IDLE);
    }

    @Test
    void audioStreamStartTransitionsToListening() {
        SttAdapter.SttSession mockSttSession = mock(SttAdapter.SttSession.class);
        when(sttAdapter.createSession(any())).thenReturn(mockSttSession);

        session.onAudioStreamStart("stream-1");

        assertThat(session.currentState()).isEqualTo(AgentState.LISTENING);
        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOf(AgentSessionEvent.StateChangeEvent.class);
        assertThat(((AgentSessionEvent.StateChangeEvent) events.get(0)).state())
                .isEqualTo(AgentState.LISTENING);
    }

    @Test
    @SuppressWarnings("unchecked")
    void fullTurnLoopWithMockedAdapters() {
        Consumer<SttEvent>[] sttConsumer = new Consumer[1];

        SttAdapter.SttSession mockSttSession = mock(SttAdapter.SttSession.class);
        when(sttAdapter.createSession(any()))
                .thenAnswer(
                        inv -> {
                            sttConsumer[0] = inv.getArgument(0);
                            return mockSttSession;
                        });

        doAnswer(
                        inv -> {
                            Consumer<LlmEvent> consumer = inv.getArgument(1);
                            consumer.accept(new LlmEvent.TokenDelta("Hello"));
                            consumer.accept(new LlmEvent.TokenDelta(" there"));
                            consumer.accept(new LlmEvent.Stop(2));
                            return null;
                        })
                .when(llmAdapter)
                .generate(any(), any());

        // Step 1: AudioStreamStart
        session.onAudioStreamStart("stream-1");
        assertThat(session.currentState()).isEqualTo(AgentState.LISTENING);

        // Step 2: Feed audio chunk (requires onAudioStreamStart to set up decoder)
        byte[] frame = "stm1testopus".getBytes();
        session.onAudioChunk(frame);

        // Step 3: SpeechEnded → triggers finalize → final transcript → thinking → LLM
        doAnswer(
                        inv -> {
                            sttConsumer[0].accept(new SttEvent.Final("What is the weather?", 2000));
                            return null;
                        })
                .when(mockSttSession)
                .finalizeStream();

        session.onSpeechEnded();

        // Verify event sequence
        assertThat(events).hasSizeGreaterThanOrEqualTo(6);

        // State: LISTENING
        assertThat(events.get(0)).isInstanceOf(AgentSessionEvent.StateChangeEvent.class);

        // TranscriptFinal
        boolean hasTranscriptFinal =
                events.stream().anyMatch(e -> e instanceof AgentSessionEvent.TranscriptFinalEvent);
        assertThat(hasTranscriptFinal).isTrue();

        // State: THINKING
        boolean hasThinking =
                events.stream()
                        .anyMatch(
                                e ->
                                        e instanceof AgentSessionEvent.StateChangeEvent sc
                                                && sc.state() == AgentState.THINKING);
        assertThat(hasThinking).isTrue();

        // LLM tokens
        long tokenCount =
                events.stream().filter(e -> e instanceof AgentSessionEvent.LlmTokenEvent).count();
        assertThat(tokenCount).isEqualTo(2);

        // State: IDLE at end
        assertThat(session.currentState()).isEqualTo(AgentState.IDLE);
    }

    @Test
    void audioChunkWithoutStreamIsIgnored() {
        session.onAudioChunk("stm1data".getBytes());
        assertThat(events).isEmpty();
    }

    @Test
    void speechEndedWithoutSessionIsHandled() {
        session.onSpeechEnded();
        assertThat(events).isEmpty();
    }

    @Test
    void audioChunkWithEmptyPcmIsHandled() {
        // Set up STT session that won't be called because frame is too short
        SttAdapter.SttSession mockSttSession = mock(SttAdapter.SttSession.class);
        when(sttAdapter.createSession(any())).thenReturn(mockSttSession);

        session.onAudioStreamStart("stream-1");

        // Send a frame that is only the 4-byte prefix — OpusDecoder returns empty pcm
        byte[] shortFrame = new byte[] {0x73, 0x74, 0x6d, 0x31};
        session.onAudioChunk(shortFrame);

        // STT session should NOT have been called with feedAudio for empty pcm
        org.mockito.Mockito.verify(mockSttSession, never()).feedAudio(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void llmToolCallRequestBranchCovered() {
        Consumer<SttEvent>[] sttConsumer = new Consumer[1];
        SttAdapter.SttSession mockSttSession = mock(SttAdapter.SttSession.class);
        when(sttAdapter.createSession(any()))
                .thenAnswer(
                        inv -> {
                            sttConsumer[0] = inv.getArgument(0);
                            return mockSttSession;
                        });

        doAnswer(
                        inv -> {
                            Consumer<LlmEvent> consumer = inv.getArgument(1);
                            // Emit tool call events (covers ToolCallRequest and ToolCallResult
                            // branches)
                            consumer.accept(
                                    new LlmEvent.ToolCallRequest(
                                            "tc1", "weather", java.util.Map.of()));
                            consumer.accept(new LlmEvent.ToolCallResult("tc1", java.util.Map.of()));
                            consumer.accept(new LlmEvent.Stop(0));
                            return null;
                        })
                .when(llmAdapter)
                .generate(any(), any());

        session.onAudioStreamStart("stream-1");
        sttConsumer[0].accept(new SttEvent.Final("hello", 1000));

        assertThat(session.currentState()).isEqualTo(AgentState.IDLE);
    }

    @Test
    @SuppressWarnings("unchecked")
    void sttPartialEventForwarded() {
        Consumer<SttEvent>[] sttConsumer = new Consumer[1];
        SttAdapter.SttSession mockSttSession = mock(SttAdapter.SttSession.class);
        when(sttAdapter.createSession(any()))
                .thenAnswer(
                        inv -> {
                            sttConsumer[0] = inv.getArgument(0);
                            return mockSttSession;
                        });

        session.onAudioStreamStart("stream-1");

        // Simulate a partial transcript event from STT
        sttConsumer[0].accept(new SttEvent.Partial("hel"));

        boolean hasPartial =
                events.stream()
                        .anyMatch(e -> e instanceof AgentSessionEvent.TranscriptPartialEvent);
        assertThat(hasPartial).isTrue();
    }
}
