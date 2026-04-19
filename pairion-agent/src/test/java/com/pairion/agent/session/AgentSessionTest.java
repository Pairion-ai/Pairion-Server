package com.pairion.agent.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import com.pairion.adapters.llm.spi.LlmAdapter;
import com.pairion.adapters.stt.spi.SttAdapter;
import com.pairion.adapters.tts.spi.TtsAdapter;
import com.pairion.agent.soul.SoulPromptProvider;
import com.pairion.agent.tools.ToolDispatcher;
import com.pairion.core.agent.AgentState;
import com.pairion.core.llm.LlmEvent;
import com.pairion.core.stt.SttEvent;
import com.pairion.core.tts.TtsCapabilities;
import com.pairion.core.tts.TtsEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Tests for {@link AgentSession} turn loop with mocked STT, LLM, and TTS. */
class AgentSessionTest {

    private SttAdapter sttAdapter;
    private LlmAdapter llmAdapter;
    private TtsAdapter ttsAdapter;
    private SoulPromptProvider soulProvider;
    private ToolDispatcher toolDispatcher;
    private List<AgentSessionEvent> events;
    private AgentSession session;

    @BeforeEach
    void setUp() {
        sttAdapter = mock(SttAdapter.class);
        llmAdapter = mock(LlmAdapter.class);
        ttsAdapter = mock(TtsAdapter.class);
        when(ttsAdapter.capabilities()).thenReturn(TtsCapabilities.unavailable());
        soulProvider = mock(SoulPromptProvider.class);
        toolDispatcher = mock(ToolDispatcher.class);
        when(soulProvider.getSystemPrompt("test-session")).thenReturn("You are Pairion.");
        events = new ArrayList<>();
        session =
                new AgentSession(
                        "test-session",
                        sttAdapter,
                        llmAdapter,
                        ttsAdapter,
                        soulProvider,
                        toolDispatcher,
                        events::add);
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

        // Step 2: Feed audio chunk
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
        assertThat(events).hasSizeGreaterThanOrEqualTo(4);

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

        // State: IDLE at end (TTS unavailable → immediate idle after LLM)
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
        SttAdapter.SttSession mockSttSession = mock(SttAdapter.SttSession.class);
        when(sttAdapter.createSession(any())).thenReturn(mockSttSession);

        session.onAudioStreamStart("stream-1");

        byte[] shortFrame = new byte[] {0x73, 0x74, 0x6d, 0x31};
        session.onAudioChunk(shortFrame);

        org.mockito.Mockito.verify(mockSttSession, never()).feedAudio(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void toolCallDispatchedAndHistoryBuilt() {
        Consumer<SttEvent>[] sttConsumer = new Consumer[1];
        SttAdapter.SttSession mockSttSession = mock(SttAdapter.SttSession.class);
        when(sttAdapter.createSession(any()))
                .thenAnswer(
                        inv -> {
                            sttConsumer[0] = inv.getArgument(0);
                            return mockSttSession;
                        });

        // First generate call: emit tool call request
        // Second generate call: emit text response
        boolean[] firstCall = {true};
        doAnswer(
                        inv -> {
                            Consumer<LlmEvent> consumer = inv.getArgument(1);
                            if (firstCall[0]) {
                                firstCall[0] = false;
                                consumer.accept(
                                        new LlmEvent.ToolCallRequest(
                                                "tc-1",
                                                "get_current_weather",
                                                Map.of("city", "Dallas")));
                                consumer.accept(new LlmEvent.Stop(0));
                            } else {
                                consumer.accept(new LlmEvent.TokenDelta("It is 72°F in Dallas."));
                                consumer.accept(new LlmEvent.Stop(5));
                            }
                            return null;
                        })
                .when(llmAdapter)
                .generate(any(), any());

        when(toolDispatcher.dispatch("get_current_weather", Map.of("city", "Dallas")))
                .thenReturn(Map.of("temperature_f", 72.0, "conditions", "Clear sky"));

        session.onAudioStreamStart("stream-1");
        sttConsumer[0].accept(new SttEvent.Final("What's the weather in Dallas?", 3000));

        // Verify ToolCallStarted event emitted
        boolean hasToolStarted =
                events.stream()
                        .anyMatch(e -> e instanceof AgentSessionEvent.ToolCallStartedEvent);
        assertThat(hasToolStarted).isTrue();

        // Verify ToolCallCompleted event emitted
        boolean hasToolCompleted =
                events.stream()
                        .anyMatch(e -> e instanceof AgentSessionEvent.ToolCallCompletedEvent);
        assertThat(hasToolCompleted).isTrue();

        // Verify LLM response token emitted
        boolean hasToken =
                events.stream().anyMatch(e -> e instanceof AgentSessionEvent.LlmTokenEvent);
        assertThat(hasToken).isTrue();

        assertThat(session.currentState()).isEqualTo(AgentState.IDLE);
    }

    @Test
    @SuppressWarnings("unchecked")
    void ttsActivatedWhenAvailableAndTextProduced() {
        when(ttsAdapter.capabilities()).thenReturn(new TtsCapabilities(true, true));

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
                            consumer.accept(new LlmEvent.TokenDelta("Hello!"));
                            consumer.accept(new LlmEvent.Stop(1));
                            return null;
                        })
                .when(llmAdapter)
                .generate(any(), any());

        // TTS speak: emit one chunk and completion
        doAnswer(
                        inv -> {
                            Consumer<TtsEvent> consumer = inv.getArgument(1);
                            consumer.accept(new TtsEvent.Chunk(new byte[]{0x01, 0x02}, true));
                            consumer.accept(new TtsEvent.Completed(100L));
                            return null;
                        })
                .when(ttsAdapter)
                .speak(any(), any());

        session.onAudioStreamStart("stream-1");
        sttConsumer[0].accept(new SttEvent.Final("hi", 500));

        // Should have AudioStreamStartEvent
        boolean hasAudioStart =
                events.stream()
                        .anyMatch(e -> e instanceof AgentSessionEvent.AudioStreamStartEvent);
        assertThat(hasAudioStart).isTrue();

        // Should have AudioChunkEvent
        boolean hasAudioChunk =
                events.stream().anyMatch(e -> e instanceof AgentSessionEvent.AudioChunkEvent);
        assertThat(hasAudioChunk).isTrue();

        // Should have AudioStreamEndEvent
        boolean hasAudioEnd =
                events.stream().anyMatch(e -> e instanceof AgentSessionEvent.AudioStreamEndEvent);
        assertThat(hasAudioEnd).isTrue();

        // Should be SPEAKING → IDLE
        assertThat(session.currentState()).isEqualTo(AgentState.IDLE);
    }

    @Test
    @SuppressWarnings("unchecked")
    void ttsErrorTransitionsToIdle() {
        when(ttsAdapter.capabilities()).thenReturn(new TtsCapabilities(true, true));

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
                            consumer.accept(new LlmEvent.TokenDelta("text"));
                            consumer.accept(new LlmEvent.Stop(1));
                            return null;
                        })
                .when(llmAdapter)
                .generate(any(), any());

        doThrow(new RuntimeException("synthesis failed")).when(ttsAdapter).speak(any(), any());

        session.onAudioStreamStart("stream-1");
        sttConsumer[0].accept(new SttEvent.Final("say something", 500));

        // Should have AudioStreamEndEvent with reason=error
        boolean hasErrorEnd =
                events.stream()
                        .filter(e -> e instanceof AgentSessionEvent.AudioStreamEndEvent)
                        .map(e -> (AgentSessionEvent.AudioStreamEndEvent) e)
                        .anyMatch(e -> "error".equals(e.reason()));
        assertThat(hasErrorEnd).isTrue();
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
        sttConsumer[0].accept(new SttEvent.Partial("hel"));

        boolean hasPartial =
                events.stream()
                        .anyMatch(e -> e instanceof AgentSessionEvent.TranscriptPartialEvent);
        assertThat(hasPartial).isTrue();
    }

    @Test
    @SuppressWarnings("unchecked")
    void maxToolRoundsExhaustedTransitionsToIdle() {
        // LLM always returns a tool call — loop should exit after MAX_TOOL_ROUNDS
        Consumer<SttEvent>[] sttConsumer = new Consumer[1];
        SttAdapter.SttSession mockSttSession = mock(SttAdapter.SttSession.class);
        when(sttAdapter.createSession(any()))
                .thenAnswer(
                        inv -> {
                            sttConsumer[0] = inv.getArgument(0);
                            return mockSttSession;
                        });

        // Always returns a tool call — no text, forces MAX_TOOL_ROUNDS
        doAnswer(
                        inv -> {
                            Consumer<LlmEvent> consumer = inv.getArgument(1);
                            consumer.accept(
                                    new LlmEvent.ToolCallRequest(
                                            "tc-x", "get_current_weather", Map.of("city", "Austin")));
                            consumer.accept(new LlmEvent.Stop(0));
                            return null;
                        })
                .when(llmAdapter)
                .generate(any(), any());

        when(toolDispatcher.dispatch("get_current_weather", Map.of("city", "Austin")))
                .thenReturn(Map.of("temperature_f", 85.0));

        session.onAudioStreamStart("stream-1");
        sttConsumer[0].accept(new SttEvent.Final("Weather in Austin?", 1000));

        // Should have reached IDLE after exhausting max rounds (text is empty)
        assertThat(session.currentState()).isEqualTo(AgentState.IDLE);
    }

    @Test
    @SuppressWarnings("unchecked")
    void handleLlmEventToolCallResultBranchCovered() {
        // Directly call handleLlmEvent to cover the ToolCallResult branch
        List<LlmEvent.ToolCallRequest> accum = new ArrayList<>();
        StringBuilder text = new StringBuilder();
        session.handleLlmEvent(
                new LlmEvent.ToolCallResult("tc-1", Map.of()),
                text,
                accum);
        assertThat(accum).isEmpty();
        assertThat(text.toString()).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void handleLlmEventStopBranchCovered() {
        List<LlmEvent.ToolCallRequest> accum = new ArrayList<>();
        StringBuilder text = new StringBuilder();
        session.handleLlmEvent(new LlmEvent.Stop(5), text, accum);
        assertThat(accum).isEmpty();
    }
}
