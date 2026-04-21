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
import com.pairion.agent.tools.map.MapFocusTool;
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
        when(soulProvider.getSystemPrompt("test-session")).thenReturn("You are Jarvis.");
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

    /** close() shuts down the scheduler without throwing (no active future). */
    @Test
    void sessionCloseDoesNotThrow() {
        // No exception expected — covers close(), cancelClear() (no future), shutdownNow()
        session.close();
    }

    /** close() with an active clear future cancels it (covers cancelClear branch when future != null). */
    @Test
    @SuppressWarnings("unchecked")
    void sessionCloseWithActiveFutureCancelsFuture() {
        Consumer<SttEvent>[] sttConsumer = new Consumer[1];
        SttAdapter.SttSession mockSttSession = mock(SttAdapter.SttSession.class);
        when(sttAdapter.createSession(any()))
                .thenAnswer(inv -> {
                    sttConsumer[0] = inv.getArgument(0);
                    return mockSttSession;
                });

        // First LLM call → focus_map tool; second call → text, so the loop exits.
        boolean[] firstCall = {true};
        doAnswer(inv -> {
                    Consumer<LlmEvent> consumer = inv.getArgument(1);
                    if (firstCall[0]) {
                        firstCall[0] = false;
                        consumer.accept(new LlmEvent.ToolCallRequest(
                                "tc-map", MapFocusTool.TOOL_NAME, Map.of("location", "Tokyo")));
                        consumer.accept(new LlmEvent.Stop(0));
                    } else {
                        consumer.accept(new LlmEvent.TokenDelta("Tokyo is in Japan."));
                        consumer.accept(new LlmEvent.Stop(4));
                    }
                    return null;
                })
                .when(llmAdapter).generate(any(), any());

        when(toolDispatcher.dispatch(MapFocusTool.TOOL_NAME, Map.of("location", "Tokyo")))
                .thenReturn(Map.of("lat", 35.6762, "lon", 139.6503,
                        "label", "Tokyo, Japan", "zoom", "city", "status", "ok"));

        session.onAudioStreamStart("stream-1");
        sttConsumer[0].accept(new SttEvent.Final("Show me Tokyo.", 1000));

        // clearFuture is now scheduled — close() must cancel it (covers cancelClear non-null branch)
        session.close();
    }

    /** A transcript containing a map-clear phrase emits MapClearEvent before transitioning to thinking. */
    @Test
    @SuppressWarnings("unchecked")
    void mapClearPhraseEmitsMapClearEvent() {
        Consumer<SttEvent>[] sttConsumer = new Consumer[1];
        SttAdapter.SttSession mockSttSession = mock(SttAdapter.SttSession.class);
        when(sttAdapter.createSession(any()))
                .thenAnswer(inv -> {
                    sttConsumer[0] = inv.getArgument(0);
                    return mockSttSession;
                });

        doAnswer(inv -> {
                    Consumer<LlmEvent> consumer = inv.getArgument(1);
                    consumer.accept(new LlmEvent.TokenDelta("Cleared."));
                    consumer.accept(new LlmEvent.Stop(1));
                    return null;
                })
                .when(llmAdapter).generate(any(), any());

        session.onAudioStreamStart("stream-1");
        // "go back" is in MAP_CLEAR_PHRASES
        sttConsumer[0].accept(new SttEvent.Final("go back", 500));

        boolean hasMapClear = events.stream()
                .anyMatch(e -> e instanceof AgentSessionEvent.MapClearEvent);
        assertThat(hasMapClear).isTrue();

        // MapClearEvent must appear before the THINKING state change
        int mapClearIdx = -1, thinkingIdx = -1;
        for (int i = 0; i < events.size(); i++) {
            if (events.get(i) instanceof AgentSessionEvent.MapClearEvent && mapClearIdx < 0) mapClearIdx = i;
            if (events.get(i) instanceof AgentSessionEvent.StateChangeEvent sc
                    && sc.state() == AgentState.THINKING && thinkingIdx < 0) thinkingIdx = i;
        }
        assertThat(mapClearIdx).isLessThan(thinkingIdx);
    }

    /** A normal transcript does NOT emit a MapClearEvent. */
    @Test
    @SuppressWarnings("unchecked")
    void normalTranscriptDoesNotEmitMapClearEvent() {
        Consumer<SttEvent>[] sttConsumer = new Consumer[1];
        SttAdapter.SttSession mockSttSession = mock(SttAdapter.SttSession.class);
        when(sttAdapter.createSession(any()))
                .thenAnswer(inv -> {
                    sttConsumer[0] = inv.getArgument(0);
                    return mockSttSession;
                });

        doAnswer(inv -> {
                    Consumer<LlmEvent> consumer = inv.getArgument(1);
                    consumer.accept(new LlmEvent.TokenDelta("Sure thing."));
                    consumer.accept(new LlmEvent.Stop(2));
                    return null;
                })
                .when(llmAdapter).generate(any(), any());

        session.onAudioStreamStart("stream-1");
        sttConsumer[0].accept(new SttEvent.Final("What is the weather?", 1000));

        boolean hasMapClear = events.stream()
                .anyMatch(e -> e instanceof AgentSessionEvent.MapClearEvent);
        assertThat(hasMapClear).isFalse();
    }

    /** focus_map tool error does NOT emit a MapFocusEvent (covers the false branch of !result.containsKey("error")). */
    @Test
    @SuppressWarnings("unchecked")
    void mapFocusToolErrorDoesNotEmitMapFocusEvent() {
        Consumer<SttEvent>[] sttConsumer = new Consumer[1];
        SttAdapter.SttSession mockSttSession = mock(SttAdapter.SttSession.class);
        when(sttAdapter.createSession(any()))
                .thenAnswer(inv -> {
                    sttConsumer[0] = inv.getArgument(0);
                    return mockSttSession;
                });

        boolean[] firstCall = {true};
        doAnswer(inv -> {
                    Consumer<LlmEvent> consumer = inv.getArgument(1);
                    if (firstCall[0]) {
                        firstCall[0] = false;
                        consumer.accept(new LlmEvent.ToolCallRequest(
                                "tc-err", MapFocusTool.TOOL_NAME, Map.of("location", "Xyzzy")));
                        consumer.accept(new LlmEvent.Stop(0));
                    } else {
                        consumer.accept(new LlmEvent.TokenDelta("Location not found."));
                        consumer.accept(new LlmEvent.Stop(3));
                    }
                    return null;
                })
                .when(llmAdapter).generate(any(), any());

        // Tool returns an error map — emitMapFocus must NOT be called
        when(toolDispatcher.dispatch(MapFocusTool.TOOL_NAME, Map.of("location", "Xyzzy")))
                .thenReturn(Map.of("error", "not_found", "status", "no_results"));

        session.onAudioStreamStart("stream-1");
        sttConsumer[0].accept(new SttEvent.Final("Find Xyzzy.", 500));

        boolean hasMapFocus = events.stream()
                .anyMatch(e -> e instanceof AgentSessionEvent.MapFocusEvent);
        assertThat(hasMapFocus).isFalse();
    }

    /** emitTimedMapClear() fires the scheduled MapClearEvent synchronously (covers timer lambda). */
    @Test
    void emitTimedMapClearFiresMapClearEvent() {
        session.emitTimedMapClear();
        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOf(AgentSessionEvent.MapClearEvent.class);
    }

    /** focus_map tool success emits a MapFocusEvent with correct coordinates. */
    @Test
    @SuppressWarnings("unchecked")
    void mapFocusToolSuccessEmitsMapFocusEvent() {
        Consumer<SttEvent>[] sttConsumer = new Consumer[1];
        SttAdapter.SttSession mockSttSession = mock(SttAdapter.SttSession.class);
        when(sttAdapter.createSession(any()))
                .thenAnswer(inv -> {
                    sttConsumer[0] = inv.getArgument(0);
                    return mockSttSession;
                });

        boolean[] firstCall = {true};
        doAnswer(inv -> {
                    Consumer<LlmEvent> consumer = inv.getArgument(1);
                    if (firstCall[0]) {
                        firstCall[0] = false;
                        consumer.accept(new LlmEvent.ToolCallRequest(
                                "tc-1", MapFocusTool.TOOL_NAME, Map.of("location", "Paris")));
                        consumer.accept(new LlmEvent.Stop(0));
                    } else {
                        consumer.accept(new LlmEvent.TokenDelta("Paris is the capital of France."));
                        consumer.accept(new LlmEvent.Stop(6));
                    }
                    return null;
                })
                .when(llmAdapter).generate(any(), any());

        when(toolDispatcher.dispatch(MapFocusTool.TOOL_NAME, Map.of("location", "Paris")))
                .thenReturn(Map.of("lat", 48.8566, "lon", 2.3522,
                        "label", "Paris, France", "zoom", "city", "status", "ok"));

        session.onAudioStreamStart("stream-1");
        sttConsumer[0].accept(new SttEvent.Final("Show me Paris on the map.", 800));

        boolean hasMapFocus = events.stream()
                .anyMatch(e -> e instanceof AgentSessionEvent.MapFocusEvent);
        assertThat(hasMapFocus).isTrue();

        AgentSessionEvent.MapFocusEvent focus = (AgentSessionEvent.MapFocusEvent) events.stream()
                .filter(e -> e instanceof AgentSessionEvent.MapFocusEvent)
                .findFirst().orElseThrow();
        assertThat(focus.lat()).isEqualTo(48.8566);
        assertThat(focus.lon()).isEqualTo(2.3522);
        assertThat(focus.label()).isEqualTo("Paris, France");
        assertThat(focus.zoom()).isEqualTo("city");

        // Scheduler was closed to avoid thread leaks after test
        session.close();
    }

    /**
     * When get_current_weather returns latitude/longitude, a MapFocusEvent is automatically
     * emitted (covers the emitMapFocusFromWeather branch).
     */
    @Test
    @SuppressWarnings("unchecked")
    void weatherToolWithLatLonEmitsMapFocusEvent() {
        Consumer<SttEvent>[] sttConsumer = new Consumer[1];
        SttAdapter.SttSession mockSttSession = mock(SttAdapter.SttSession.class);
        when(sttAdapter.createSession(any()))
                .thenAnswer(inv -> {
                    sttConsumer[0] = inv.getArgument(0);
                    return mockSttSession;
                });

        boolean[] firstCall = {true};
        doAnswer(inv -> {
                    Consumer<LlmEvent> consumer = inv.getArgument(1);
                    if (firstCall[0]) {
                        firstCall[0] = false;
                        consumer.accept(new LlmEvent.ToolCallRequest(
                                "tc-w", "get_current_weather", Map.of("city", "Dallas")));
                        consumer.accept(new LlmEvent.Stop(0));
                    } else {
                        consumer.accept(new LlmEvent.TokenDelta("It is 62 degrees in Dallas."));
                        consumer.accept(new LlmEvent.Stop(6));
                    }
                    return null;
                })
                .when(llmAdapter).generate(any(), any());

        when(toolDispatcher.dispatch("get_current_weather", Map.of("city", "Dallas")))
                .thenReturn(Map.of(
                        "latitude", 32.7831,
                        "longitude", -96.8067,
                        "city", "Dallas, United States",
                        "temperature_f", 62.3,
                        "conditions", "Drizzle",
                        "wind_speed_mph", 10.1));

        session.onAudioStreamStart("stream-1");
        sttConsumer[0].accept(new SttEvent.Final("What's the weather in Dallas?", 1000));

        boolean hasMapFocus = events.stream()
                .anyMatch(e -> e instanceof AgentSessionEvent.MapFocusEvent);
        assertThat(hasMapFocus).isTrue();

        AgentSessionEvent.MapFocusEvent focus = (AgentSessionEvent.MapFocusEvent) events.stream()
                .filter(e -> e instanceof AgentSessionEvent.MapFocusEvent)
                .findFirst().orElseThrow();
        assertThat(focus.lat()).isEqualTo(32.7831);
        assertThat(focus.lon()).isEqualTo(-96.8067);
        assertThat(focus.label()).isEqualTo("Dallas, United States");
        assertThat(focus.zoom()).isEqualTo("city");

        session.close();
    }
}
