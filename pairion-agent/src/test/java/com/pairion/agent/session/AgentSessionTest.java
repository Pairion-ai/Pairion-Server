package com.pairion.agent.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import com.pairion.adapters.data.adsb.AdsbAircraft;
import com.pairion.adapters.data.adsb.AdsbDataAdapter;
import com.pairion.adapters.data.weathercurrent.WeatherCurrentDataAdapter;
import com.pairion.adapters.data.weatherradar.WeatherRadarDataAdapter;
import com.pairion.adapters.data.weatherradar.WeatherRadarFrame;
import com.pairion.adapters.data.weatherradar.WeatherRadarSnapshot;
import com.pairion.adapters.llm.spi.LlmAdapter;
import com.pairion.adapters.stt.spi.SttAdapter;
import com.pairion.adapters.tts.spi.TtsAdapter;
import com.pairion.agent.soul.SoulPromptProvider;
import com.pairion.agent.tools.ToolDispatcher;
import com.pairion.agent.tools.layer.AddOverlayTool;
import com.pairion.agent.tools.layer.ClearOverlaysTool;
import com.pairion.agent.tools.layer.RemoveOverlayTool;
import com.pairion.agent.tools.layer.SetBackgroundTool;
import com.pairion.agent.tools.map.MapFocusTool;
import com.pairion.agent.tools.scene.ShowAdsbRadarTool;
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
        when(soulProvider.getSystemPrompt(eq("test-session"), anyString(), anyString()))
                .thenReturn("You are Jarvis.");
        events = new ArrayList<>();
        session =
                new AgentSession(
                        "test-session",
                        sttAdapter,
                        llmAdapter,
                        ttsAdapter,
                        soulProvider,
                        toolDispatcher,
                        null,
                        null,
                        null,
                        null,
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
                events.stream().anyMatch(e -> e instanceof AgentSessionEvent.ToolCallStartedEvent);
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
                            consumer.accept(new TtsEvent.Chunk(new byte[] {0x01, 0x02}, true));
                            consumer.accept(new TtsEvent.Completed(100L));
                            return null;
                        })
                .when(ttsAdapter)
                .speak(any(), any());

        session.onAudioStreamStart("stream-1");
        sttConsumer[0].accept(new SttEvent.Final("hi", 500));

        // Should have AudioStreamStartEvent
        boolean hasAudioStart =
                events.stream().anyMatch(e -> e instanceof AgentSessionEvent.AudioStreamStartEvent);
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
                                            "tc-x",
                                            "get_current_weather",
                                            Map.of("city", "Austin")));
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
        session.handleLlmEvent(new LlmEvent.ToolCallResult("tc-1", Map.of()), text, accum);
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

    /**
     * close() with an active clear future cancels it (covers cancelClear branch when future !=
     * null).
     */
    @Test
    @SuppressWarnings("unchecked")
    void sessionCloseWithActiveFutureCancelsFuture() {
        Consumer<SttEvent>[] sttConsumer = new Consumer[1];
        SttAdapter.SttSession mockSttSession = mock(SttAdapter.SttSession.class);
        when(sttAdapter.createSession(any()))
                .thenAnswer(
                        inv -> {
                            sttConsumer[0] = inv.getArgument(0);
                            return mockSttSession;
                        });

        // First LLM call → focus_map tool; second call → text, so the loop exits.
        boolean[] firstCall = {true};
        doAnswer(
                        inv -> {
                            Consumer<LlmEvent> consumer = inv.getArgument(1);
                            if (firstCall[0]) {
                                firstCall[0] = false;
                                consumer.accept(
                                        new LlmEvent.ToolCallRequest(
                                                "tc-map",
                                                MapFocusTool.TOOL_NAME,
                                                Map.of("location", "Tokyo")));
                                consumer.accept(new LlmEvent.Stop(0));
                            } else {
                                consumer.accept(new LlmEvent.TokenDelta("Tokyo is in Japan."));
                                consumer.accept(new LlmEvent.Stop(4));
                            }
                            return null;
                        })
                .when(llmAdapter)
                .generate(any(), any());

        when(toolDispatcher.dispatch(MapFocusTool.TOOL_NAME, Map.of("location", "Tokyo")))
                .thenReturn(
                        Map.of(
                                "lat",
                                35.6762,
                                "lon",
                                139.6503,
                                "label",
                                "Tokyo, Japan",
                                "zoom",
                                "city",
                                "status",
                                "ok"));

        session.onAudioStreamStart("stream-1");
        sttConsumer[0].accept(new SttEvent.Final("Show me Tokyo.", 1000));

        // clearFuture is now scheduled — close() must cancel it (covers cancelClear non-null
        // branch)
        session.close();
    }

    /**
     * A transcript containing a map-clear phrase emits MapClearEvent before transitioning to
     * thinking.
     */
    @Test
    @SuppressWarnings("unchecked")
    void mapClearPhraseEmitsMapClearEvent() {
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
                            consumer.accept(new LlmEvent.TokenDelta("Cleared."));
                            consumer.accept(new LlmEvent.Stop(1));
                            return null;
                        })
                .when(llmAdapter)
                .generate(any(), any());

        session.onAudioStreamStart("stream-1");
        // "go back" is in MAP_CLEAR_PHRASES
        sttConsumer[0].accept(new SttEvent.Final("go back", 500));

        boolean hasMapClear =
                events.stream().anyMatch(e -> e instanceof AgentSessionEvent.MapClearEvent);
        assertThat(hasMapClear).isTrue();

        // MapClearEvent must appear before the THINKING state change
        int mapClearIdx = -1, thinkingIdx = -1;
        for (int i = 0; i < events.size(); i++) {
            if (events.get(i) instanceof AgentSessionEvent.MapClearEvent && mapClearIdx < 0)
                mapClearIdx = i;
            if (events.get(i) instanceof AgentSessionEvent.StateChangeEvent sc
                    && sc.state() == AgentState.THINKING
                    && thinkingIdx < 0) thinkingIdx = i;
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
                .thenAnswer(
                        inv -> {
                            sttConsumer[0] = inv.getArgument(0);
                            return mockSttSession;
                        });

        doAnswer(
                        inv -> {
                            Consumer<LlmEvent> consumer = inv.getArgument(1);
                            consumer.accept(new LlmEvent.TokenDelta("Sure thing."));
                            consumer.accept(new LlmEvent.Stop(2));
                            return null;
                        })
                .when(llmAdapter)
                .generate(any(), any());

        session.onAudioStreamStart("stream-1");
        sttConsumer[0].accept(new SttEvent.Final("What is the weather?", 1000));

        boolean hasMapClear =
                events.stream().anyMatch(e -> e instanceof AgentSessionEvent.MapClearEvent);
        assertThat(hasMapClear).isFalse();
    }

    /**
     * focus_map tool error does NOT emit a MapFocusEvent (covers the false branch of
     * !result.containsKey("error")).
     */
    @Test
    @SuppressWarnings("unchecked")
    void mapFocusToolErrorDoesNotEmitMapFocusEvent() {
        Consumer<SttEvent>[] sttConsumer = new Consumer[1];
        SttAdapter.SttSession mockSttSession = mock(SttAdapter.SttSession.class);
        when(sttAdapter.createSession(any()))
                .thenAnswer(
                        inv -> {
                            sttConsumer[0] = inv.getArgument(0);
                            return mockSttSession;
                        });

        boolean[] firstCall = {true};
        doAnswer(
                        inv -> {
                            Consumer<LlmEvent> consumer = inv.getArgument(1);
                            if (firstCall[0]) {
                                firstCall[0] = false;
                                consumer.accept(
                                        new LlmEvent.ToolCallRequest(
                                                "tc-err",
                                                MapFocusTool.TOOL_NAME,
                                                Map.of("location", "Xyzzy")));
                                consumer.accept(new LlmEvent.Stop(0));
                            } else {
                                consumer.accept(new LlmEvent.TokenDelta("Location not found."));
                                consumer.accept(new LlmEvent.Stop(3));
                            }
                            return null;
                        })
                .when(llmAdapter)
                .generate(any(), any());

        // Tool returns an error map — emitMapFocus must NOT be called
        when(toolDispatcher.dispatch(MapFocusTool.TOOL_NAME, Map.of("location", "Xyzzy")))
                .thenReturn(Map.of("error", "not_found", "status", "no_results"));

        session.onAudioStreamStart("stream-1");
        sttConsumer[0].accept(new SttEvent.Final("Find Xyzzy.", 500));

        boolean hasMapFocus =
                events.stream().anyMatch(e -> e instanceof AgentSessionEvent.MapFocusEvent);
        assertThat(hasMapFocus).isFalse();
    }

    /**
     * emitTimedMapClear() fires the scheduled MapClearEvent synchronously (covers timer lambda).
     */
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
                .thenAnswer(
                        inv -> {
                            sttConsumer[0] = inv.getArgument(0);
                            return mockSttSession;
                        });

        boolean[] firstCall = {true};
        doAnswer(
                        inv -> {
                            Consumer<LlmEvent> consumer = inv.getArgument(1);
                            if (firstCall[0]) {
                                firstCall[0] = false;
                                consumer.accept(
                                        new LlmEvent.ToolCallRequest(
                                                "tc-1",
                                                MapFocusTool.TOOL_NAME,
                                                Map.of("location", "Paris")));
                                consumer.accept(new LlmEvent.Stop(0));
                            } else {
                                consumer.accept(
                                        new LlmEvent.TokenDelta("Paris is the capital of France."));
                                consumer.accept(new LlmEvent.Stop(6));
                            }
                            return null;
                        })
                .when(llmAdapter)
                .generate(any(), any());

        when(toolDispatcher.dispatch(MapFocusTool.TOOL_NAME, Map.of("location", "Paris")))
                .thenReturn(
                        Map.of(
                                "lat",
                                48.8566,
                                "lon",
                                2.3522,
                                "label",
                                "Paris, France",
                                "zoom",
                                "city",
                                "status",
                                "ok"));

        session.onAudioStreamStart("stream-1");
        sttConsumer[0].accept(new SttEvent.Final("Show me Paris on the map.", 800));

        boolean hasMapFocus =
                events.stream().anyMatch(e -> e instanceof AgentSessionEvent.MapFocusEvent);
        assertThat(hasMapFocus).isTrue();

        AgentSessionEvent.MapFocusEvent focus =
                (AgentSessionEvent.MapFocusEvent)
                        events.stream()
                                .filter(e -> e instanceof AgentSessionEvent.MapFocusEvent)
                                .findFirst()
                                .orElseThrow();
        assertThat(focus.lat()).isEqualTo(48.8566);
        assertThat(focus.lon()).isEqualTo(2.3522);
        assertThat(focus.label()).isEqualTo("Paris, France");
        assertThat(focus.zoom()).isEqualTo("city");

        // Scheduler was closed to avoid thread leaks after test
        session.close();
    }

    /**
     * A dismissal transcript emits ConversationEndedEvent (covers the true branch of
     * isConversationEndPhrase).
     */
    @Test
    @SuppressWarnings("unchecked")
    void conversationEndPhraseEmitsConversationEndedEvent() {
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
                            consumer.accept(new LlmEvent.TokenDelta("Goodbye!"));
                            consumer.accept(new LlmEvent.Stop(1));
                            return null;
                        })
                .when(llmAdapter)
                .generate(any(), any());

        session.onAudioStreamStart("stream-1");
        sttConsumer[0].accept(new SttEvent.Final("goodbye", 500));

        boolean hasConversationEnded =
                events.stream()
                        .anyMatch(e -> e instanceof AgentSessionEvent.ConversationEndedEvent);
        assertThat(hasConversationEnded).isTrue();
    }

    /**
     * A normal transcript does NOT emit a ConversationEndedEvent (covers the false branch of
     * isConversationEndPhrase).
     */
    @Test
    @SuppressWarnings("unchecked")
    void normalTranscriptDoesNotEmitConversationEndedEvent() {
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
                            consumer.accept(new LlmEvent.TokenDelta("Sure."));
                            consumer.accept(new LlmEvent.Stop(1));
                            return null;
                        })
                .when(llmAdapter)
                .generate(any(), any());

        session.onAudioStreamStart("stream-1");
        sttConsumer[0].accept(new SttEvent.Final("What is the capital of France?", 800));

        boolean hasConversationEnded =
                events.stream()
                        .anyMatch(e -> e instanceof AgentSessionEvent.ConversationEndedEvent);
        assertThat(hasConversationEnded).isFalse();
    }

    /**
     * When get_current_weather returns latitude/longitude, a MapFocusEvent is automatically emitted
     * (covers the emitMapFocusFromWeather branch).
     */
    @Test
    @SuppressWarnings("unchecked")
    void weatherToolWithLatLonEmitsMapFocusEvent() {
        Consumer<SttEvent>[] sttConsumer = new Consumer[1];
        SttAdapter.SttSession mockSttSession = mock(SttAdapter.SttSession.class);
        when(sttAdapter.createSession(any()))
                .thenAnswer(
                        inv -> {
                            sttConsumer[0] = inv.getArgument(0);
                            return mockSttSession;
                        });

        boolean[] firstCall = {true};
        doAnswer(
                        inv -> {
                            Consumer<LlmEvent> consumer = inv.getArgument(1);
                            if (firstCall[0]) {
                                firstCall[0] = false;
                                consumer.accept(
                                        new LlmEvent.ToolCallRequest(
                                                "tc-w",
                                                "get_current_weather",
                                                Map.of("city", "Dallas")));
                                consumer.accept(new LlmEvent.Stop(0));
                            } else {
                                consumer.accept(
                                        new LlmEvent.TokenDelta("It is 62 degrees in Dallas."));
                                consumer.accept(new LlmEvent.Stop(6));
                            }
                            return null;
                        })
                .when(llmAdapter)
                .generate(any(), any());

        when(toolDispatcher.dispatch("get_current_weather", Map.of("city", "Dallas")))
                .thenReturn(
                        Map.of(
                                "latitude", 32.7831,
                                "longitude", -96.8067,
                                "city", "Dallas, United States",
                                "temperature_f", 62.3,
                                "conditions", "Drizzle",
                                "wind_speed_mph", 10.1));

        session.onAudioStreamStart("stream-1");
        sttConsumer[0].accept(new SttEvent.Final("What's the weather in Dallas?", 1000));

        boolean hasMapFocus =
                events.stream().anyMatch(e -> e instanceof AgentSessionEvent.MapFocusEvent);
        assertThat(hasMapFocus).isTrue();

        AgentSessionEvent.MapFocusEvent focus =
                (AgentSessionEvent.MapFocusEvent)
                        events.stream()
                                .filter(e -> e instanceof AgentSessionEvent.MapFocusEvent)
                                .findFirst()
                                .orElseThrow();
        assertThat(focus.lat()).isEqualTo(32.7831);
        assertThat(focus.lon()).isEqualTo(-96.8067);
        assertThat(focus.label()).isEqualTo("Dallas, United States");
        assertThat(focus.zoom()).isEqualTo("city");

        session.close();
    }

    /**
     * set_background tool success emits a BackgroundChangeEvent with the correct background ID and
     * transition.
     */
    @Test
    @SuppressWarnings("unchecked")
    void setBackgroundToolSuccessEmitsBackgroundChangeEvent() {
        Consumer<SttEvent>[] sttConsumer = new Consumer[1];
        SttAdapter.SttSession mockSttSession = mock(SttAdapter.SttSession.class);
        when(sttAdapter.createSession(any()))
                .thenAnswer(
                        inv -> {
                            sttConsumer[0] = inv.getArgument(0);
                            return mockSttSession;
                        });

        boolean[] firstCall = {true};
        doAnswer(
                        inv -> {
                            Consumer<LlmEvent> consumer = inv.getArgument(1);
                            if (firstCall[0]) {
                                firstCall[0] = false;
                                consumer.accept(
                                        new LlmEvent.ToolCallRequest(
                                                "tc-bg",
                                                SetBackgroundTool.TOOL_NAME,
                                                Map.of(
                                                        "background_id",
                                                        "globe",
                                                        "transition",
                                                        "crossfade")));
                                consumer.accept(new LlmEvent.Stop(0));
                            } else {
                                consumer.accept(new LlmEvent.TokenDelta("Switching to the globe."));
                                consumer.accept(new LlmEvent.Stop(5));
                            }
                            return null;
                        })
                .when(llmAdapter)
                .generate(any(), any());

        when(toolDispatcher.dispatch(
                        SetBackgroundTool.TOOL_NAME,
                        Map.of("background_id", "globe", "transition", "crossfade")))
                .thenReturn(
                        Map.of(
                                "status",
                                "background_set",
                                "background_id",
                                "globe",
                                "transition",
                                "crossfade"));

        session.onAudioStreamStart("stream-1");
        sttConsumer[0].accept(new SttEvent.Final("Show me the globe.", 800));

        boolean hasBackgroundChange =
                events.stream().anyMatch(e -> e instanceof AgentSessionEvent.BackgroundChangeEvent);
        assertThat(hasBackgroundChange).isTrue();

        AgentSessionEvent.BackgroundChangeEvent bc =
                (AgentSessionEvent.BackgroundChangeEvent)
                        events.stream()
                                .filter(e -> e instanceof AgentSessionEvent.BackgroundChangeEvent)
                                .findFirst()
                                .orElseThrow();
        assertThat(bc.backgroundId()).isEqualTo("globe");
        assertThat(bc.transition()).isEqualTo("crossfade");
    }

    /**
     * set_background tool error does NOT emit a BackgroundChangeEvent (covers the false branch of
     * !result.containsKey("error")).
     */
    @Test
    @SuppressWarnings("unchecked")
    void setBackgroundToolErrorDoesNotEmitBackgroundChangeEvent() {
        Consumer<SttEvent>[] sttConsumer = new Consumer[1];
        SttAdapter.SttSession mockSttSession = mock(SttAdapter.SttSession.class);
        when(sttAdapter.createSession(any()))
                .thenAnswer(
                        inv -> {
                            sttConsumer[0] = inv.getArgument(0);
                            return mockSttSession;
                        });

        boolean[] firstCall = {true};
        doAnswer(
                        inv -> {
                            Consumer<LlmEvent> consumer = inv.getArgument(1);
                            if (firstCall[0]) {
                                firstCall[0] = false;
                                consumer.accept(
                                        new LlmEvent.ToolCallRequest(
                                                "tc-bg-err",
                                                SetBackgroundTool.TOOL_NAME,
                                                Map.of("background_id", "unknown-bg")));
                                consumer.accept(new LlmEvent.Stop(0));
                            } else {
                                consumer.accept(new LlmEvent.TokenDelta("Background not found."));
                                consumer.accept(new LlmEvent.Stop(3));
                            }
                            return null;
                        })
                .when(llmAdapter)
                .generate(any(), any());

        when(toolDispatcher.dispatch(
                        SetBackgroundTool.TOOL_NAME, Map.of("background_id", "unknown-bg")))
                .thenReturn(
                        Map.of(
                                "error",
                                "unknown_background",
                                "message",
                                "Background not registered"));

        session.onAudioStreamStart("stream-1");
        sttConsumer[0].accept(new SttEvent.Final("Load the unknown background.", 600));

        boolean hasBackgroundChange =
                events.stream().anyMatch(e -> e instanceof AgentSessionEvent.BackgroundChangeEvent);
        assertThat(hasBackgroundChange).isFalse();
    }

    /** add_overlay tool success emits an OverlayAddEvent with the correct overlay ID. */
    @Test
    @SuppressWarnings("unchecked")
    void addOverlayToolSuccessEmitsOverlayAddEvent() {
        Consumer<SttEvent>[] sttConsumer = new Consumer[1];
        SttAdapter.SttSession mockSttSession = mock(SttAdapter.SttSession.class);
        when(sttAdapter.createSession(any()))
                .thenAnswer(
                        inv -> {
                            sttConsumer[0] = inv.getArgument(0);
                            return mockSttSession;
                        });

        boolean[] firstCall = {true};
        doAnswer(
                        inv -> {
                            Consumer<LlmEvent> consumer = inv.getArgument(1);
                            if (firstCall[0]) {
                                firstCall[0] = false;
                                consumer.accept(
                                        new LlmEvent.ToolCallRequest(
                                                "tc-overlay-add",
                                                com.pairion.agent.tools.layer.AddOverlayTool
                                                        .TOOL_NAME,
                                                Map.of("overlay_id", "adsb")));
                                consumer.accept(new LlmEvent.Stop(0));
                            } else {
                                consumer.accept(new LlmEvent.TokenDelta("Overlay added."));
                                consumer.accept(new LlmEvent.Stop(2));
                            }
                            return null;
                        })
                .when(llmAdapter)
                .generate(any(), any());

        when(toolDispatcher.dispatch(
                        com.pairion.agent.tools.layer.AddOverlayTool.TOOL_NAME,
                        Map.of("overlay_id", "adsb")))
                .thenReturn(Map.of("status", "overlay_added", "overlay_id", "adsb"));

        session.onAudioStreamStart("stream-1");
        sttConsumer[0].accept(new SttEvent.Final("Add the ADS-B overlay.", 400));

        boolean hasOverlayAdd =
                events.stream()
                        .anyMatch(
                                e ->
                                        e instanceof AgentSessionEvent.OverlayAddEvent oa
                                                && "adsb".equals(oa.overlayId()));
        assertThat(hasOverlayAdd).isTrue();
    }

    /** add_overlay tool error does NOT emit an OverlayAddEvent. */
    @Test
    @SuppressWarnings("unchecked")
    void addOverlayToolErrorDoesNotEmitOverlayAddEvent() {
        Consumer<SttEvent>[] sttConsumer = new Consumer[1];
        SttAdapter.SttSession mockSttSession = mock(SttAdapter.SttSession.class);
        when(sttAdapter.createSession(any()))
                .thenAnswer(
                        inv -> {
                            sttConsumer[0] = inv.getArgument(0);
                            return mockSttSession;
                        });

        boolean[] firstCall = {true};
        doAnswer(
                        inv -> {
                            Consumer<LlmEvent> consumer = inv.getArgument(1);
                            if (firstCall[0]) {
                                firstCall[0] = false;
                                consumer.accept(
                                        new LlmEvent.ToolCallRequest(
                                                "tc-overlay-add-err",
                                                com.pairion.agent.tools.layer.AddOverlayTool
                                                        .TOOL_NAME,
                                                Map.of()));
                                consumer.accept(new LlmEvent.Stop(0));
                            } else {
                                consumer.accept(new LlmEvent.TokenDelta("Could not add overlay."));
                                consumer.accept(new LlmEvent.Stop(2));
                            }
                            return null;
                        })
                .when(llmAdapter)
                .generate(any(), any());

        when(toolDispatcher.dispatch(
                        com.pairion.agent.tools.layer.AddOverlayTool.TOOL_NAME, Map.of()))
                .thenReturn(Map.of("error", "missing_parameter", "message", "overlay_id required"));

        session.onAudioStreamStart("stream-1");
        sttConsumer[0].accept(new SttEvent.Final("Add overlay.", 300));

        boolean hasOverlayAdd =
                events.stream().anyMatch(e -> e instanceof AgentSessionEvent.OverlayAddEvent);
        assertThat(hasOverlayAdd).isFalse();
    }

    /** remove_overlay tool success emits an OverlayRemoveEvent with the correct overlay ID. */
    @Test
    @SuppressWarnings("unchecked")
    void removeOverlayToolSuccessEmitsOverlayRemoveEvent() {
        Consumer<SttEvent>[] sttConsumer = new Consumer[1];
        SttAdapter.SttSession mockSttSession = mock(SttAdapter.SttSession.class);
        when(sttAdapter.createSession(any()))
                .thenAnswer(
                        inv -> {
                            sttConsumer[0] = inv.getArgument(0);
                            return mockSttSession;
                        });

        boolean[] firstCall = {true};
        doAnswer(
                        inv -> {
                            Consumer<LlmEvent> consumer = inv.getArgument(1);
                            if (firstCall[0]) {
                                firstCall[0] = false;
                                consumer.accept(
                                        new LlmEvent.ToolCallRequest(
                                                "tc-overlay-remove",
                                                com.pairion.agent.tools.layer.RemoveOverlayTool
                                                        .TOOL_NAME,
                                                Map.of("overlay_id", "adsb")));
                                consumer.accept(new LlmEvent.Stop(0));
                            } else {
                                consumer.accept(new LlmEvent.TokenDelta("Overlay removed."));
                                consumer.accept(new LlmEvent.Stop(2));
                            }
                            return null;
                        })
                .when(llmAdapter)
                .generate(any(), any());

        when(toolDispatcher.dispatch(
                        com.pairion.agent.tools.layer.RemoveOverlayTool.TOOL_NAME,
                        Map.of("overlay_id", "adsb")))
                .thenReturn(Map.of("status", "overlay_removed", "overlay_id", "adsb"));

        session.onAudioStreamStart("stream-1");
        sttConsumer[0].accept(new SttEvent.Final("Remove the ADS-B overlay.", 400));

        boolean hasOverlayRemove =
                events.stream()
                        .anyMatch(
                                e ->
                                        e instanceof AgentSessionEvent.OverlayRemoveEvent or
                                                && "adsb".equals(or.overlayId()));
        assertThat(hasOverlayRemove).isTrue();
    }

    /** remove_overlay tool error does NOT emit an OverlayRemoveEvent. */
    @Test
    @SuppressWarnings("unchecked")
    void removeOverlayToolErrorDoesNotEmitOverlayRemoveEvent() {
        Consumer<SttEvent>[] sttConsumer = new Consumer[1];
        SttAdapter.SttSession mockSttSession = mock(SttAdapter.SttSession.class);
        when(sttAdapter.createSession(any()))
                .thenAnswer(
                        inv -> {
                            sttConsumer[0] = inv.getArgument(0);
                            return mockSttSession;
                        });

        boolean[] firstCall = {true};
        doAnswer(
                        inv -> {
                            Consumer<LlmEvent> consumer = inv.getArgument(1);
                            if (firstCall[0]) {
                                firstCall[0] = false;
                                consumer.accept(
                                        new LlmEvent.ToolCallRequest(
                                                "tc-overlay-remove-err",
                                                com.pairion.agent.tools.layer.RemoveOverlayTool
                                                        .TOOL_NAME,
                                                Map.of()));
                                consumer.accept(new LlmEvent.Stop(0));
                            } else {
                                consumer.accept(
                                        new LlmEvent.TokenDelta("Could not remove overlay."));
                                consumer.accept(new LlmEvent.Stop(2));
                            }
                            return null;
                        })
                .when(llmAdapter)
                .generate(any(), any());

        when(toolDispatcher.dispatch(
                        com.pairion.agent.tools.layer.RemoveOverlayTool.TOOL_NAME, Map.of()))
                .thenReturn(Map.of("error", "missing_parameter", "message", "overlay_id required"));

        session.onAudioStreamStart("stream-1");
        sttConsumer[0].accept(new SttEvent.Final("Remove overlay.", 300));

        boolean hasOverlayRemove =
                events.stream().anyMatch(e -> e instanceof AgentSessionEvent.OverlayRemoveEvent);
        assertThat(hasOverlayRemove).isFalse();
    }

    /** clear_overlays tool success emits an OverlayClearEvent. */
    @Test
    @SuppressWarnings("unchecked")
    void clearOverlaysToolSuccessEmitsOverlayClearEvent() {
        Consumer<SttEvent>[] sttConsumer = new Consumer[1];
        SttAdapter.SttSession mockSttSession = mock(SttAdapter.SttSession.class);
        when(sttAdapter.createSession(any()))
                .thenAnswer(
                        inv -> {
                            sttConsumer[0] = inv.getArgument(0);
                            return mockSttSession;
                        });

        boolean[] firstCall = {true};
        doAnswer(
                        inv -> {
                            Consumer<LlmEvent> consumer = inv.getArgument(1);
                            if (firstCall[0]) {
                                firstCall[0] = false;
                                consumer.accept(
                                        new LlmEvent.ToolCallRequest(
                                                "tc-clear",
                                                com.pairion.agent.tools.layer.ClearOverlaysTool
                                                        .TOOL_NAME,
                                                Map.of()));
                                consumer.accept(new LlmEvent.Stop(0));
                            } else {
                                consumer.accept(new LlmEvent.TokenDelta("Overlays cleared."));
                                consumer.accept(new LlmEvent.Stop(2));
                            }
                            return null;
                        })
                .when(llmAdapter)
                .generate(any(), any());

        when(toolDispatcher.dispatch(
                        com.pairion.agent.tools.layer.ClearOverlaysTool.TOOL_NAME, Map.of()))
                .thenReturn(Map.of("status", "overlays_cleared"));

        session.onAudioStreamStart("stream-1");
        sttConsumer[0].accept(new SttEvent.Final("Clear all overlays.", 300));

        boolean hasOverlayClear =
                events.stream().anyMatch(e -> e instanceof AgentSessionEvent.OverlayClearEvent);
        assertThat(hasOverlayClear).isTrue();
    }

    /** clear_overlays tool error does NOT emit an OverlayClearEvent. */
    @Test
    @SuppressWarnings("unchecked")
    void clearOverlaysToolErrorDoesNotEmitOverlayClearEvent() {
        Consumer<SttEvent>[] sttConsumer = new Consumer[1];
        SttAdapter.SttSession mockSttSession = mock(SttAdapter.SttSession.class);
        when(sttAdapter.createSession(any()))
                .thenAnswer(
                        inv -> {
                            sttConsumer[0] = inv.getArgument(0);
                            return mockSttSession;
                        });

        boolean[] firstCall = {true};
        doAnswer(
                        inv -> {
                            Consumer<LlmEvent> consumer = inv.getArgument(1);
                            if (firstCall[0]) {
                                firstCall[0] = false;
                                consumer.accept(
                                        new LlmEvent.ToolCallRequest(
                                                "tc-clear-err",
                                                com.pairion.agent.tools.layer.ClearOverlaysTool
                                                        .TOOL_NAME,
                                                Map.of()));
                                consumer.accept(new LlmEvent.Stop(0));
                            } else {
                                consumer.accept(new LlmEvent.TokenDelta("Could not clear."));
                                consumer.accept(new LlmEvent.Stop(2));
                            }
                            return null;
                        })
                .when(llmAdapter)
                .generate(any(), any());

        when(toolDispatcher.dispatch(
                        com.pairion.agent.tools.layer.ClearOverlaysTool.TOOL_NAME, Map.of()))
                .thenReturn(Map.of("error", "internal", "message", "unexpected error"));

        session.onAudioStreamStart("stream-1");
        sttConsumer[0].accept(new SttEvent.Final("Clear overlays.", 300));

        boolean hasOverlayClear =
                events.stream().anyMatch(e -> e instanceof AgentSessionEvent.OverlayClearEvent);
        assertThat(hasOverlayClear).isFalse();
    }

    /**
     * activateDefaultOsmView() emits a BackgroundChangeEvent with backgroundId="osm" and DFW
     * params.
     */
    @Test
    void activateDefaultOsmViewEmitsBackgroundChangeEvent() {
        session.activateDefaultOsmView();

        boolean hasOsmBackground =
                events.stream()
                        .anyMatch(
                                e ->
                                        e instanceof AgentSessionEvent.BackgroundChangeEvent bc
                                                && "osm".equals(bc.backgroundId())
                                                && bc.params() != null
                                                && bc.params().containsKey("zoom")
                                                && bc.params().containsKey("center_lat")
                                                && bc.params().containsKey("center_lon")
                                                && "instant".equals(bc.transition()));
        assertThat(hasOsmBackground).isTrue();
    }

    /**
     * activateAdsbRadar() emits BackgroundChangeEvent and OverlayAddEvent (covers the public entry
     * point).
     */
    @Test
    void activateAdsbRadarEmitsLayerEvents() {
        session.activateAdsbRadar();

        boolean hasBackground =
                events.stream()
                        .anyMatch(
                                e ->
                                        e instanceof AgentSessionEvent.BackgroundChangeEvent bc
                                                && "vfr".equals(bc.backgroundId()));
        assertThat(hasBackground).isTrue();

        boolean hasOverlay =
                events.stream()
                        .anyMatch(
                                e ->
                                        e instanceof AgentSessionEvent.OverlayAddEvent oa
                                                && "adsb".equals(oa.overlayId()));
        assertThat(hasOverlay).isTrue();
    }

    /**
     * show_adsb_radar tool success emits BackgroundChangeEvent("vfr") + OverlayAddEvent("adsb") and
     * starts adapter.
     */
    @Test
    @SuppressWarnings("unchecked")
    void showAdsbRadarToolSuccessEmitsBackgroundAndOverlayAndStartsAdapter() {
        AdsbDataAdapter adsbAdapter = mock(AdsbDataAdapter.class);
        AgentSession sessionWithAdsb =
                new AgentSession(
                        "test-adsb",
                        sttAdapter,
                        llmAdapter,
                        ttsAdapter,
                        soulProvider,
                        toolDispatcher,
                        adsbAdapter,
                        null,
                        null,
                        null,
                        events::add);
        when(soulProvider.getSystemPrompt(eq("test-adsb"), anyString(), anyString()))
                .thenReturn("You are Jarvis.");

        Consumer<SttEvent>[] sttConsumer = new Consumer[1];
        SttAdapter.SttSession mockSttSession = mock(SttAdapter.SttSession.class);
        when(sttAdapter.createSession(any()))
                .thenAnswer(
                        inv -> {
                            sttConsumer[0] = inv.getArgument(0);
                            return mockSttSession;
                        });

        boolean[] firstCall = {true};
        doAnswer(
                        inv -> {
                            Consumer<LlmEvent> consumer = inv.getArgument(1);
                            if (firstCall[0]) {
                                firstCall[0] = false;
                                consumer.accept(
                                        new LlmEvent.ToolCallRequest(
                                                "tc-adsb", ShowAdsbRadarTool.TOOL_NAME, Map.of()));
                                consumer.accept(new LlmEvent.Stop(0));
                            } else {
                                consumer.accept(new LlmEvent.TokenDelta("Radar is now active."));
                                consumer.accept(new LlmEvent.Stop(4));
                            }
                            return null;
                        })
                .when(llmAdapter)
                .generate(any(), any());

        when(toolDispatcher.dispatch(ShowAdsbRadarTool.TOOL_NAME, Map.of()))
                .thenReturn(
                        Map.of(
                                "status",
                                "adsb_radar_activated",
                                "background_id",
                                "vfr",
                                "overlay_id",
                                "adsb"));

        sessionWithAdsb.onAudioStreamStart("stream-1");
        sttConsumer[0].accept(new SttEvent.Final("Show me the radar.", 500));

        boolean hasBackgroundChange =
                events.stream()
                        .anyMatch(
                                e ->
                                        e instanceof AgentSessionEvent.BackgroundChangeEvent bc
                                                && "vfr".equals(bc.backgroundId()));
        assertThat(hasBackgroundChange).isTrue();

        boolean hasOverlayAdd =
                events.stream()
                        .anyMatch(
                                e ->
                                        e instanceof AgentSessionEvent.OverlayAddEvent oa
                                                && "adsb".equals(oa.overlayId()));
        assertThat(hasOverlayAdd).isTrue();

        org.mockito.Mockito.verify(adsbAdapter).startPolling(any());
        sessionWithAdsb.close();
        org.mockito.Mockito.verify(adsbAdapter).stopPolling();
    }

    /**
     * show_adsb_radar tool error does NOT emit BackgroundChangeEvent and does NOT start adapter.
     */
    @Test
    @SuppressWarnings("unchecked")
    void showAdsbRadarToolErrorDoesNotEmitBackgroundChange() {
        AdsbDataAdapter adsbAdapter = mock(AdsbDataAdapter.class);
        AgentSession sessionWithAdsb =
                new AgentSession(
                        "test-adsb-err",
                        sttAdapter,
                        llmAdapter,
                        ttsAdapter,
                        soulProvider,
                        toolDispatcher,
                        adsbAdapter,
                        null,
                        null,
                        null,
                        events::add);
        when(soulProvider.getSystemPrompt(eq("test-adsb-err"), anyString(), anyString()))
                .thenReturn("You are Jarvis.");

        Consumer<SttEvent>[] sttConsumer = new Consumer[1];
        SttAdapter.SttSession mockSttSession = mock(SttAdapter.SttSession.class);
        when(sttAdapter.createSession(any()))
                .thenAnswer(
                        inv -> {
                            sttConsumer[0] = inv.getArgument(0);
                            return mockSttSession;
                        });

        boolean[] firstCall = {true};
        doAnswer(
                        inv -> {
                            Consumer<LlmEvent> consumer = inv.getArgument(1);
                            if (firstCall[0]) {
                                firstCall[0] = false;
                                consumer.accept(
                                        new LlmEvent.ToolCallRequest(
                                                "tc-adsb-err",
                                                ShowAdsbRadarTool.TOOL_NAME,
                                                Map.of()));
                                consumer.accept(new LlmEvent.Stop(0));
                            } else {
                                consumer.accept(new LlmEvent.TokenDelta("Radar unavailable."));
                                consumer.accept(new LlmEvent.Stop(2));
                            }
                            return null;
                        })
                .when(llmAdapter)
                .generate(any(), any());

        when(toolDispatcher.dispatch(ShowAdsbRadarTool.TOOL_NAME, Map.of()))
                .thenReturn(Map.of("error", "unavailable", "message", "ADS-B not configured"));

        sessionWithAdsb.onAudioStreamStart("stream-1");
        sttConsumer[0].accept(new SttEvent.Final("Show me the radar.", 500));

        boolean hasBackgroundChange =
                events.stream()
                        .anyMatch(
                                e ->
                                        e instanceof AgentSessionEvent.BackgroundChangeEvent bc
                                                && "vfr".equals(bc.backgroundId()));
        assertThat(hasBackgroundChange).isFalse();
        org.mockito.Mockito.verify(adsbAdapter, never()).startPolling(any());
        sessionWithAdsb.close();
    }

    /** SceneDataPushEvent is emitted when ADSB adapter delivers aircraft list. */
    @Test
    void adsbAdapterSinkEmitsSceneDataPushEvent() {
        AdsbDataAdapter adsbAdapter = mock(AdsbDataAdapter.class);
        AgentSession sessionWithAdsb =
                new AgentSession(
                        "test-push",
                        sttAdapter,
                        llmAdapter,
                        ttsAdapter,
                        soulProvider,
                        toolDispatcher,
                        adsbAdapter,
                        null,
                        null,
                        null,
                        events::add);

        // Capture the sink registered with the adapter
        java.util.concurrent.atomic.AtomicReference<
                        java.util.function.Consumer<java.util.List<AdsbAircraft>>>
                capturedSink = new java.util.concurrent.atomic.AtomicReference<>();
        org.mockito.Mockito.doAnswer(
                        inv -> {
                            capturedSink.set(inv.getArgument(0));
                            return null;
                        })
                .when(adsbAdapter)
                .startPolling(any());

        // Manually trigger emitAdsbRadar by dispatching through the tool call path
        // Use a tool call that triggers the adsb path
        Consumer<SttEvent>[] sttConsumer = new Consumer[1];
        SttAdapter.SttSession mockSttSession = mock(SttAdapter.SttSession.class);
        when(sttAdapter.createSession(any()))
                .thenAnswer(
                        inv -> {
                            sttConsumer[0] = inv.getArgument(0);
                            return mockSttSession;
                        });
        when(soulProvider.getSystemPrompt(eq("test-push"), anyString(), anyString()))
                .thenReturn("You are Jarvis.");

        boolean[] firstCall = {true};
        doAnswer(
                        inv -> {
                            Consumer<LlmEvent> consumer = inv.getArgument(1);
                            if (firstCall[0]) {
                                firstCall[0] = false;
                                consumer.accept(
                                        new LlmEvent.ToolCallRequest(
                                                "tc-push", ShowAdsbRadarTool.TOOL_NAME, Map.of()));
                                consumer.accept(new LlmEvent.Stop(0));
                            } else {
                                consumer.accept(new LlmEvent.TokenDelta("Done."));
                                consumer.accept(new LlmEvent.Stop(1));
                            }
                            return null;
                        })
                .when(llmAdapter)
                .generate(any(), any());

        when(toolDispatcher.dispatch(ShowAdsbRadarTool.TOOL_NAME, Map.of()))
                .thenReturn(
                        Map.of(
                                "status",
                                "adsb_radar_activated",
                                "background_id",
                                "vfr",
                                "overlay_id",
                                "adsb"));

        sessionWithAdsb.onAudioStreamStart("stream-1");
        sttConsumer[0].accept(new SttEvent.Final("Show radar.", 500));

        assertThat(capturedSink.get()).isNotNull();

        // Simulate the adapter delivering aircraft data
        AdsbAircraft plane =
                new AdsbAircraft(
                        "abc123", "UAL1", 35.0, -97.0, 10000.0, 480.0, 90.0, 0.0, false, "N123AB",
                        "B738", "KDFW", "KLAX");
        capturedSink.get().accept(java.util.List.of(plane));

        boolean hasPush =
                events.stream()
                        .anyMatch(
                                e ->
                                        e instanceof AgentSessionEvent.SceneDataPushEvent sdp
                                                && "adsb".equals(sdp.modelId()));
        assertThat(hasPush).isTrue();
        sessionWithAdsb.close();
    }

    /**
     * show_adsb_radar with null adsbDataAdapter still emits BackgroundChangeEvent + OverlayAddEvent
     * (covers null branch).
     */
    @Test
    @SuppressWarnings("unchecked")
    void showAdsbRadarWithNullAdapterEmitsBackgroundAndOverlayOnly() {
        // Default session has null adsbDataAdapter
        Consumer<SttEvent>[] sttConsumer = new Consumer[1];
        SttAdapter.SttSession mockSttSession = mock(SttAdapter.SttSession.class);
        when(sttAdapter.createSession(any()))
                .thenAnswer(
                        inv -> {
                            sttConsumer[0] = inv.getArgument(0);
                            return mockSttSession;
                        });

        boolean[] firstCall = {true};
        doAnswer(
                        inv -> {
                            Consumer<LlmEvent> consumer = inv.getArgument(1);
                            if (firstCall[0]) {
                                firstCall[0] = false;
                                consumer.accept(
                                        new LlmEvent.ToolCallRequest(
                                                "tc-null-adsb",
                                                ShowAdsbRadarTool.TOOL_NAME,
                                                Map.of()));
                                consumer.accept(new LlmEvent.Stop(0));
                            } else {
                                consumer.accept(new LlmEvent.TokenDelta("Radar active."));
                                consumer.accept(new LlmEvent.Stop(2));
                            }
                            return null;
                        })
                .when(llmAdapter)
                .generate(any(), any());

        when(toolDispatcher.dispatch(ShowAdsbRadarTool.TOOL_NAME, Map.of()))
                .thenReturn(
                        Map.of(
                                "status",
                                "adsb_radar_activated",
                                "background_id",
                                "vfr",
                                "overlay_id",
                                "adsb"));

        session.onAudioStreamStart("stream-1");
        sttConsumer[0].accept(new SttEvent.Final("Show radar please.", 500));

        // BackgroundChangeEvent and OverlayAddEvent still emitted even when adsbDataAdapter is null
        boolean hasBackgroundChange =
                events.stream()
                        .anyMatch(
                                e ->
                                        e instanceof AgentSessionEvent.BackgroundChangeEvent bc
                                                && "vfr".equals(bc.backgroundId()));
        assertThat(hasBackgroundChange).isTrue();

        boolean hasOverlayAdd =
                events.stream()
                        .anyMatch(
                                e ->
                                        e instanceof AgentSessionEvent.OverlayAddEvent oa
                                                && "adsb".equals(oa.overlayId()));
        assertThat(hasOverlayAdd).isTrue();
    }

    /**
     * When get_current_weather returns latitude but NOT longitude, no MapFocusEvent is emitted
     * (covers the false branch of result.containsKey("longitude")).
     */
    @Test
    @SuppressWarnings("unchecked")
    void weatherToolWithLatButNoLonDoesNotEmitMapFocus() {
        Consumer<SttEvent>[] sttConsumer = new Consumer[1];
        SttAdapter.SttSession mockSttSession = mock(SttAdapter.SttSession.class);
        when(sttAdapter.createSession(any()))
                .thenAnswer(
                        inv -> {
                            sttConsumer[0] = inv.getArgument(0);
                            return mockSttSession;
                        });

        boolean[] firstCall = {true};
        doAnswer(
                        inv -> {
                            Consumer<LlmEvent> consumer = inv.getArgument(1);
                            if (firstCall[0]) {
                                firstCall[0] = false;
                                consumer.accept(
                                        new LlmEvent.ToolCallRequest(
                                                "tc-p",
                                                "get_current_weather",
                                                Map.of("city", "Paris")));
                                consumer.accept(new LlmEvent.Stop(0));
                            } else {
                                consumer.accept(new LlmEvent.TokenDelta("Partial data for Paris."));
                                consumer.accept(new LlmEvent.Stop(4));
                            }
                            return null;
                        })
                .when(llmAdapter)
                .generate(any(), any());

        // Result has latitude but no longitude — should NOT trigger emitMapFocusFromWeather
        when(toolDispatcher.dispatch("get_current_weather", Map.of("city", "Paris")))
                .thenReturn(Map.of("latitude", 48.8566, "temperature_f", 55.0));

        session.onAudioStreamStart("stream-1");
        sttConsumer[0].accept(new SttEvent.Final("Weather in Paris?", 1000));

        boolean hasMapFocus =
                events.stream().anyMatch(e -> e instanceof AgentSessionEvent.MapFocusEvent);
        assertThat(hasMapFocus).isFalse();

        session.close();
    }

    // ── Weather radar overlay lifecycle ───────────────────────────────────────

    /** add_overlay("weather_radar") starts weather radar polling. */
    @Test
    @SuppressWarnings("unchecked")
    void addOverlayWeatherRadarStartsPolling() {
        WeatherRadarDataAdapter wxAdapter = mock(WeatherRadarDataAdapter.class);
        AgentSession sessionWithWx =
                new AgentSession(
                        "test-wx",
                        sttAdapter,
                        llmAdapter,
                        ttsAdapter,
                        soulProvider,
                        toolDispatcher,
                        null,
                        wxAdapter,
                        null,
                        null,
                        events::add);
        when(soulProvider.getSystemPrompt(eq("test-wx"), anyString(), anyString()))
                .thenReturn("You are Jarvis.");

        Consumer<SttEvent>[] sttConsumer = new Consumer[1];
        SttAdapter.SttSession mockSttSession = mock(SttAdapter.SttSession.class);
        when(sttAdapter.createSession(any()))
                .thenAnswer(
                        inv -> {
                            sttConsumer[0] = inv.getArgument(0);
                            return mockSttSession;
                        });

        boolean[] firstCall = {true};
        doAnswer(
                        inv -> {
                            Consumer<LlmEvent> consumer = inv.getArgument(1);
                            if (firstCall[0]) {
                                firstCall[0] = false;
                                consumer.accept(
                                        new LlmEvent.ToolCallRequest(
                                                "tc-wx",
                                                AddOverlayTool.TOOL_NAME,
                                                Map.of("overlay_id", "weather_radar")));
                                consumer.accept(new LlmEvent.Stop(0));
                            } else {
                                consumer.accept(new LlmEvent.TokenDelta("Radar overlay active."));
                                consumer.accept(new LlmEvent.Stop(2));
                            }
                            return null;
                        })
                .when(llmAdapter)
                .generate(any(), any());

        when(toolDispatcher.dispatch(
                        AddOverlayTool.TOOL_NAME, Map.of("overlay_id", "weather_radar")))
                .thenReturn(Map.of("status", "overlay_added", "overlay_id", "weather_radar"));

        sessionWithWx.onAudioStreamStart("stream-1");
        sttConsumer[0].accept(new SttEvent.Final("Show weather radar.", 500));

        org.mockito.Mockito.verify(wxAdapter).startPolling(any());
        sessionWithWx.close();
        org.mockito.Mockito.verify(wxAdapter).stopPolling();
    }

    /** remove_overlay("weather_radar") stops weather radar polling. */
    @Test
    @SuppressWarnings("unchecked")
    void removeOverlayWeatherRadarStopsPolling() {
        WeatherRadarDataAdapter wxAdapter = mock(WeatherRadarDataAdapter.class);
        AgentSession sessionWithWx =
                new AgentSession(
                        "test-wx-remove",
                        sttAdapter,
                        llmAdapter,
                        ttsAdapter,
                        soulProvider,
                        toolDispatcher,
                        null,
                        wxAdapter,
                        null,
                        null,
                        events::add);
        when(soulProvider.getSystemPrompt(eq("test-wx-remove"), anyString(), anyString()))
                .thenReturn("You are Jarvis.");

        Consumer<SttEvent>[] sttConsumer = new Consumer[1];
        SttAdapter.SttSession mockSttSession = mock(SttAdapter.SttSession.class);
        when(sttAdapter.createSession(any()))
                .thenAnswer(
                        inv -> {
                            sttConsumer[0] = inv.getArgument(0);
                            return mockSttSession;
                        });

        boolean[] firstCall = {true};
        doAnswer(
                        inv -> {
                            Consumer<LlmEvent> consumer = inv.getArgument(1);
                            if (firstCall[0]) {
                                firstCall[0] = false;
                                consumer.accept(
                                        new LlmEvent.ToolCallRequest(
                                                "tc-wx-rm",
                                                RemoveOverlayTool.TOOL_NAME,
                                                Map.of("overlay_id", "weather_radar")));
                                consumer.accept(new LlmEvent.Stop(0));
                            } else {
                                consumer.accept(new LlmEvent.TokenDelta("Radar overlay removed."));
                                consumer.accept(new LlmEvent.Stop(2));
                            }
                            return null;
                        })
                .when(llmAdapter)
                .generate(any(), any());

        when(toolDispatcher.dispatch(
                        RemoveOverlayTool.TOOL_NAME, Map.of("overlay_id", "weather_radar")))
                .thenReturn(Map.of("status", "overlay_removed", "overlay_id", "weather_radar"));

        sessionWithWx.onAudioStreamStart("stream-1");
        sttConsumer[0].accept(new SttEvent.Final("Hide the weather radar.", 500));

        org.mockito.Mockito.verify(wxAdapter).stopPolling();
        sessionWithWx.close();
    }

    /** clear_overlays stops weather radar polling. */
    @Test
    @SuppressWarnings("unchecked")
    void clearOverlaysStopsWeatherRadarPolling() {
        WeatherRadarDataAdapter wxAdapter = mock(WeatherRadarDataAdapter.class);
        AgentSession sessionWithWx =
                new AgentSession(
                        "test-wx-clear",
                        sttAdapter,
                        llmAdapter,
                        ttsAdapter,
                        soulProvider,
                        toolDispatcher,
                        null,
                        wxAdapter,
                        null,
                        null,
                        events::add);
        when(soulProvider.getSystemPrompt(eq("test-wx-clear"), anyString(), anyString()))
                .thenReturn("You are Jarvis.");

        Consumer<SttEvent>[] sttConsumer = new Consumer[1];
        SttAdapter.SttSession mockSttSession = mock(SttAdapter.SttSession.class);
        when(sttAdapter.createSession(any()))
                .thenAnswer(
                        inv -> {
                            sttConsumer[0] = inv.getArgument(0);
                            return mockSttSession;
                        });

        boolean[] firstCall = {true};
        doAnswer(
                        inv -> {
                            Consumer<LlmEvent> consumer = inv.getArgument(1);
                            if (firstCall[0]) {
                                firstCall[0] = false;
                                consumer.accept(
                                        new LlmEvent.ToolCallRequest(
                                                "tc-clear", ClearOverlaysTool.TOOL_NAME, Map.of()));
                                consumer.accept(new LlmEvent.Stop(0));
                            } else {
                                consumer.accept(new LlmEvent.TokenDelta("All overlays cleared."));
                                consumer.accept(new LlmEvent.Stop(2));
                            }
                            return null;
                        })
                .when(llmAdapter)
                .generate(any(), any());

        when(toolDispatcher.dispatch(ClearOverlaysTool.TOOL_NAME, Map.of()))
                .thenReturn(Map.of("status", "overlays_cleared"));

        sessionWithWx.onAudioStreamStart("stream-1");
        sttConsumer[0].accept(new SttEvent.Final("Clear all overlays.", 500));

        org.mockito.Mockito.verify(wxAdapter).stopPolling();
        sessionWithWx.close();
    }

    /** remove_overlay("weather_radar") with null weatherRadarDataAdapter does not throw. */
    @Test
    @SuppressWarnings("unchecked")
    void removeOverlayWeatherRadarWithNullAdapterDoesNotThrow() {
        // Default session has null weatherRadarDataAdapter
        Consumer<SttEvent>[] sttConsumer = new Consumer[1];
        SttAdapter.SttSession mockSttSession = mock(SttAdapter.SttSession.class);
        when(sttAdapter.createSession(any()))
                .thenAnswer(
                        inv -> {
                            sttConsumer[0] = inv.getArgument(0);
                            return mockSttSession;
                        });

        boolean[] firstCall = {true};
        doAnswer(
                        inv -> {
                            Consumer<LlmEvent> consumer = inv.getArgument(1);
                            if (firstCall[0]) {
                                firstCall[0] = false;
                                consumer.accept(
                                        new LlmEvent.ToolCallRequest(
                                                "tc-wx-rm-null",
                                                RemoveOverlayTool.TOOL_NAME,
                                                Map.of("overlay_id", "weather_radar")));
                                consumer.accept(new LlmEvent.Stop(0));
                            } else {
                                consumer.accept(new LlmEvent.TokenDelta("Radar overlay removed."));
                                consumer.accept(new LlmEvent.Stop(2));
                            }
                            return null;
                        })
                .when(llmAdapter)
                .generate(any(), any());

        when(toolDispatcher.dispatch(
                        RemoveOverlayTool.TOOL_NAME, Map.of("overlay_id", "weather_radar")))
                .thenReturn(Map.of("status", "overlay_removed", "overlay_id", "weather_radar"));

        session.onAudioStreamStart("stream-1");
        sttConsumer[0].accept(new SttEvent.Final("Hide the weather radar.", 500));

        boolean hasOverlayRemove =
                events.stream()
                        .anyMatch(
                                e ->
                                        e instanceof AgentSessionEvent.OverlayRemoveEvent or
                                                && "weather_radar".equals(or.overlayId()));
        assertThat(hasOverlayRemove).isTrue();
    }

    /** add_overlay("weather_radar") with null weatherRadarDataAdapter does not throw. */
    @Test
    @SuppressWarnings("unchecked")
    void addOverlayWeatherRadarWithNullAdapterDoesNotThrow() {
        // Default session has null weatherRadarDataAdapter
        Consumer<SttEvent>[] sttConsumer = new Consumer[1];
        SttAdapter.SttSession mockSttSession = mock(SttAdapter.SttSession.class);
        when(sttAdapter.createSession(any()))
                .thenAnswer(
                        inv -> {
                            sttConsumer[0] = inv.getArgument(0);
                            return mockSttSession;
                        });

        boolean[] firstCall = {true};
        doAnswer(
                        inv -> {
                            Consumer<LlmEvent> consumer = inv.getArgument(1);
                            if (firstCall[0]) {
                                firstCall[0] = false;
                                consumer.accept(
                                        new LlmEvent.ToolCallRequest(
                                                "tc-wx-null",
                                                AddOverlayTool.TOOL_NAME,
                                                Map.of("overlay_id", "weather_radar")));
                                consumer.accept(new LlmEvent.Stop(0));
                            } else {
                                consumer.accept(new LlmEvent.TokenDelta("Radar overlay active."));
                                consumer.accept(new LlmEvent.Stop(2));
                            }
                            return null;
                        })
                .when(llmAdapter)
                .generate(any(), any());

        when(toolDispatcher.dispatch(
                        AddOverlayTool.TOOL_NAME, Map.of("overlay_id", "weather_radar")))
                .thenReturn(Map.of("status", "overlay_added", "overlay_id", "weather_radar"));

        session.onAudioStreamStart("stream-1");
        sttConsumer[0].accept(new SttEvent.Final("Show weather radar.", 500));

        boolean hasOverlayAdd =
                events.stream()
                        .anyMatch(
                                e ->
                                        e instanceof AgentSessionEvent.OverlayAddEvent oa
                                                && "weather_radar".equals(oa.overlayId()));
        assertThat(hasOverlayAdd).isTrue();
    }

    // ── WeatherCurrent overlay lifecycle ──────────────────────────────────────

    /** add_overlay("weather_current") with a city param starts the weather current fetch. */
    @Test
    @SuppressWarnings("unchecked")
    void emitOverlayAddWeatherCurrentStartsFetch() {
        WeatherCurrentDataAdapter wxCurrentAdapter = mock(WeatherCurrentDataAdapter.class);
        AgentSession wxSession =
                new AgentSession(
                        "wx-session",
                        sttAdapter,
                        llmAdapter,
                        ttsAdapter,
                        soulProvider,
                        toolDispatcher,
                        null,
                        null,
                        wxCurrentAdapter,
                        null,
                        events::add);
        when(soulProvider.getSystemPrompt(eq("wx-session"), anyString(), anyString()))
                .thenReturn("You are Jarvis.");

        when(toolDispatcher.dispatch(
                        AddOverlayTool.TOOL_NAME,
                        Map.of(
                                "overlay_id",
                                "weather_current",
                                "params",
                                Map.of("city", "Dallas"))))
                .thenReturn(
                        Map.of(
                                "status", "overlay_added",
                                "overlay_id", "weather_current",
                                "params", Map.of("city", "Dallas")));

        Consumer<SttEvent>[] sttConsumer = new Consumer[1];
        SttAdapter.SttSession mockSttSession = mock(SttAdapter.SttSession.class);
        when(sttAdapter.createSession(any()))
                .thenAnswer(
                        inv -> {
                            sttConsumer[0] = inv.getArgument(0);
                            return mockSttSession;
                        });

        boolean[] firstCall = {true};
        doAnswer(
                        inv -> {
                            Consumer<LlmEvent> consumer = inv.getArgument(1);
                            if (firstCall[0]) {
                                firstCall[0] = false;
                                consumer.accept(
                                        new LlmEvent.ToolCallRequest(
                                                "tc-wx",
                                                AddOverlayTool.TOOL_NAME,
                                                Map.of(
                                                        "overlay_id",
                                                        "weather_current",
                                                        "params",
                                                        Map.of("city", "Dallas"))));
                                consumer.accept(new LlmEvent.Stop(0));
                            } else {
                                consumer.accept(new LlmEvent.TokenDelta("Here is the weather."));
                                consumer.accept(new LlmEvent.Stop(2));
                            }
                            return null;
                        })
                .when(llmAdapter)
                .generate(any(), any());

        wxSession.onAudioStreamStart("s1");
        sttConsumer[0].accept(new SttEvent.Final("Show me the weather in Dallas", 1000));

        org.mockito.Mockito.verify(wxCurrentAdapter)
                .start(
                        org.mockito.ArgumentMatchers.eq("Dallas"),
                        org.mockito.ArgumentMatchers.any());
        wxSession.close();
    }

    /** add_overlay("weather_current") with no city param does NOT start the fetch. */
    @Test
    @SuppressWarnings("unchecked")
    void emitOverlayAddWeatherCurrentNullParamsDoesNotStart() {
        WeatherCurrentDataAdapter wxCurrentAdapter = mock(WeatherCurrentDataAdapter.class);
        AgentSession wxSession =
                new AgentSession(
                        "wx-session2",
                        sttAdapter,
                        llmAdapter,
                        ttsAdapter,
                        soulProvider,
                        toolDispatcher,
                        null,
                        null,
                        wxCurrentAdapter,
                        null,
                        events::add);
        when(soulProvider.getSystemPrompt(eq("wx-session2"), anyString(), anyString()))
                .thenReturn("You are Jarvis.");

        when(toolDispatcher.dispatch(
                        AddOverlayTool.TOOL_NAME, Map.of("overlay_id", "weather_current")))
                .thenReturn(Map.of("status", "overlay_added", "overlay_id", "weather_current"));

        Consumer<SttEvent>[] sttConsumer = new Consumer[1];
        SttAdapter.SttSession mockSttSession = mock(SttAdapter.SttSession.class);
        when(sttAdapter.createSession(any()))
                .thenAnswer(
                        inv -> {
                            sttConsumer[0] = inv.getArgument(0);
                            return mockSttSession;
                        });

        boolean[] firstCall2 = {true};
        doAnswer(
                        inv -> {
                            Consumer<LlmEvent> consumer = inv.getArgument(1);
                            if (firstCall2[0]) {
                                firstCall2[0] = false;
                                consumer.accept(
                                        new LlmEvent.ToolCallRequest(
                                                "tc-wx2",
                                                AddOverlayTool.TOOL_NAME,
                                                Map.of("overlay_id", "weather_current")));
                                consumer.accept(new LlmEvent.Stop(0));
                            } else {
                                consumer.accept(new LlmEvent.TokenDelta("Done."));
                                consumer.accept(new LlmEvent.Stop(1));
                            }
                            return null;
                        })
                .when(llmAdapter)
                .generate(any(), any());

        wxSession.onAudioStreamStart("s2");
        sttConsumer[0].accept(new SttEvent.Final("Show weather", 1000));

        org.mockito.Mockito.verify(wxCurrentAdapter, never()).start(any(), any());
        wxSession.close();
    }

    /** WeatherCurrentDataAdapter sink emits SceneDataPushEvent with model ID "weather_current". */
    @Test
    @SuppressWarnings("unchecked")
    void weatherCurrentAdapterSinkEmitsSceneDataPushEvent() {
        WeatherCurrentDataAdapter wxCurrentAdapter = mock(WeatherCurrentDataAdapter.class);
        AgentSession wxSession =
                new AgentSession(
                        "wx-push-session",
                        sttAdapter,
                        llmAdapter,
                        ttsAdapter,
                        soulProvider,
                        toolDispatcher,
                        null,
                        null,
                        wxCurrentAdapter,
                        null,
                        events::add);
        when(soulProvider.getSystemPrompt(eq("wx-push-session"), anyString(), anyString()))
                .thenReturn("You are Jarvis.");

        java.util.concurrent.atomic.AtomicReference<
                        java.util.function.Consumer<
                                com.pairion.adapters.data.weathercurrent.WeatherCurrentSnapshot>>
                capturedSink = new java.util.concurrent.atomic.AtomicReference<>();
        org.mockito.Mockito.doAnswer(
                        inv -> {
                            capturedSink.set(inv.getArgument(1));
                            return null;
                        })
                .when(wxCurrentAdapter)
                .start(any(), any());

        when(toolDispatcher.dispatch(
                        AddOverlayTool.TOOL_NAME,
                        Map.of(
                                "overlay_id",
                                "weather_current",
                                "params",
                                Map.of("city", "Austin"))))
                .thenReturn(
                        Map.of(
                                "status", "overlay_added",
                                "overlay_id", "weather_current",
                                "params", Map.of("city", "Austin")));

        Consumer<SttEvent>[] sttConsumer = new Consumer[1];
        SttAdapter.SttSession mockSttSession = mock(SttAdapter.SttSession.class);
        when(sttAdapter.createSession(any()))
                .thenAnswer(
                        inv -> {
                            sttConsumer[0] = inv.getArgument(0);
                            return mockSttSession;
                        });

        boolean[] firstCall4 = {true};
        doAnswer(
                        inv -> {
                            Consumer<LlmEvent> consumer = inv.getArgument(1);
                            if (firstCall4[0]) {
                                firstCall4[0] = false;
                                consumer.accept(
                                        new LlmEvent.ToolCallRequest(
                                                "tc-wx-push",
                                                AddOverlayTool.TOOL_NAME,
                                                Map.of(
                                                        "overlay_id",
                                                        "weather_current",
                                                        "params",
                                                        Map.of("city", "Austin"))));
                                consumer.accept(new LlmEvent.Stop(0));
                            } else {
                                consumer.accept(new LlmEvent.TokenDelta("Austin weather ready."));
                                consumer.accept(new LlmEvent.Stop(2));
                            }
                            return null;
                        })
                .when(llmAdapter)
                .generate(any(), any());

        wxSession.onAudioStreamStart("s4");
        sttConsumer[0].accept(new SttEvent.Final("Austin weather", 500));

        assertThat(capturedSink.get()).isNotNull();

        // Simulate adapter delivering a snapshot — exercises the sink lambda
        com.pairion.adapters.data.weathercurrent.WeatherCurrentSnapshot snap =
                new com.pairion.adapters.data.weathercurrent.WeatherCurrentSnapshot(
                        "Austin, US",
                        85.0,
                        90.0,
                        95.0,
                        75.0,
                        60,
                        10.0,
                        180,
                        "Clear sky",
                        0.0,
                        1010.0);
        capturedSink.get().accept(snap);

        boolean hasPush =
                events.stream()
                        .anyMatch(
                                e ->
                                        e instanceof AgentSessionEvent.SceneDataPushEvent sdp
                                                && "weather_current".equals(sdp.modelId()));
        assertThat(hasPush).isTrue();
        wxSession.close();
    }

    /** add_overlay("weather_current") with null weatherCurrentDataAdapter does not throw. */
    @Test
    @SuppressWarnings("unchecked")
    void emitOverlayAddWeatherCurrentNullAdapterSkipped() {
        // Default session (setUp) has null weatherCurrentDataAdapter
        when(toolDispatcher.dispatch(
                        AddOverlayTool.TOOL_NAME,
                        Map.of("overlay_id", "weather_current", "params", Map.of("city", "NYC"))))
                .thenReturn(
                        Map.of(
                                "status", "overlay_added",
                                "overlay_id", "weather_current",
                                "params", Map.of("city", "NYC")));

        Consumer<SttEvent>[] sttConsumer = new Consumer[1];
        SttAdapter.SttSession mockSttSession = mock(SttAdapter.SttSession.class);
        when(sttAdapter.createSession(any()))
                .thenAnswer(
                        inv -> {
                            sttConsumer[0] = inv.getArgument(0);
                            return mockSttSession;
                        });

        boolean[] firstCall3 = {true};
        doAnswer(
                        inv -> {
                            Consumer<LlmEvent> consumer = inv.getArgument(1);
                            if (firstCall3[0]) {
                                firstCall3[0] = false;
                                consumer.accept(
                                        new LlmEvent.ToolCallRequest(
                                                "tc-wx3",
                                                AddOverlayTool.TOOL_NAME,
                                                Map.of(
                                                        "overlay_id",
                                                        "weather_current",
                                                        "params",
                                                        Map.of("city", "NYC"))));
                                consumer.accept(new LlmEvent.Stop(0));
                            } else {
                                consumer.accept(new LlmEvent.TokenDelta("NYC weather."));
                                consumer.accept(new LlmEvent.Stop(2));
                            }
                            return null;
                        })
                .when(llmAdapter)
                .generate(any(), any());

        session.onAudioStreamStart("s3");
        sttConsumer[0].accept(new SttEvent.Final("Show NYC weather", 1000));

        // No NPE — null adapter is handled gracefully
        assertThat(session.currentState()).isEqualTo(AgentState.IDLE);
    }

    /** WeatherRadarDataAdapter sink emits SceneDataPushEvent with model ID "weather_radar". */
    @Test
    @SuppressWarnings("unchecked")
    void weatherRadarAdapterSinkEmitsSceneDataPushEvent() {
        WeatherRadarDataAdapter wxAdapter = mock(WeatherRadarDataAdapter.class);
        AgentSession sessionWithWx =
                new AgentSession(
                        "test-wx-push",
                        sttAdapter,
                        llmAdapter,
                        ttsAdapter,
                        soulProvider,
                        toolDispatcher,
                        null,
                        wxAdapter,
                        null,
                        null,
                        events::add);
        when(soulProvider.getSystemPrompt(eq("test-wx-push"), anyString(), anyString()))
                .thenReturn("You are Jarvis.");

        java.util.concurrent.atomic.AtomicReference<Consumer<WeatherRadarSnapshot>> capturedSink =
                new java.util.concurrent.atomic.AtomicReference<>();
        org.mockito.Mockito.doAnswer(
                        inv -> {
                            capturedSink.set(inv.getArgument(0));
                            return null;
                        })
                .when(wxAdapter)
                .startPolling(any());

        Consumer<SttEvent>[] sttConsumer = new Consumer[1];
        SttAdapter.SttSession mockSttSession = mock(SttAdapter.SttSession.class);
        when(sttAdapter.createSession(any()))
                .thenAnswer(
                        inv -> {
                            sttConsumer[0] = inv.getArgument(0);
                            return mockSttSession;
                        });

        boolean[] firstCall = {true};
        doAnswer(
                        inv -> {
                            Consumer<LlmEvent> consumer = inv.getArgument(1);
                            if (firstCall[0]) {
                                firstCall[0] = false;
                                consumer.accept(
                                        new LlmEvent.ToolCallRequest(
                                                "tc-wx-p",
                                                AddOverlayTool.TOOL_NAME,
                                                Map.of("overlay_id", "weather_radar")));
                                consumer.accept(new LlmEvent.Stop(0));
                            } else {
                                consumer.accept(new LlmEvent.TokenDelta("Radar active."));
                                consumer.accept(new LlmEvent.Stop(2));
                            }
                            return null;
                        })
                .when(llmAdapter)
                .generate(any(), any());

        when(toolDispatcher.dispatch(
                        AddOverlayTool.TOOL_NAME, Map.of("overlay_id", "weather_radar")))
                .thenReturn(Map.of("status", "overlay_added", "overlay_id", "weather_radar"));

        sessionWithWx.onAudioStreamStart("stream-1");
        sttConsumer[0].accept(new SttEvent.Final("Show weather radar.", 500));

        assertThat(capturedSink.get()).isNotNull();

        WeatherRadarSnapshot snapshot =
                new WeatherRadarSnapshot(
                        "https://tilecache.rainviewer.com",
                        List.of(new WeatherRadarFrame(1713739200L, "/v2/radar/1713739200")),
                        "/v2/radar/1713739200",
                        256,
                        4,
                        "1_1");
        capturedSink.get().accept(snapshot);

        boolean hasPush =
                events.stream()
                        .anyMatch(
                                e ->
                                        e instanceof AgentSessionEvent.SceneDataPushEvent sdp
                                                && "weather_radar".equals(sdp.modelId()));
        assertThat(hasPush).isTrue();
        sessionWithWx.close();
    }

    // ── Memory integration tests ─────────────────────────────────────────────

    @Test
    void onAudioStreamStartCallsStartEpisodeWhenMemoryServicePresent() {
        com.pairion.memory.service.MemoryService memoryService =
                mock(com.pairion.memory.service.MemoryService.class);
        AgentSession sessionWithMemory =
                new AgentSession(
                        "mem-session",
                        sttAdapter,
                        llmAdapter,
                        ttsAdapter,
                        soulProvider,
                        toolDispatcher,
                        null,
                        null,
                        null,
                        memoryService,
                        events::add);

        SttAdapter.SttSession mockSttSession = mock(SttAdapter.SttSession.class);
        when(sttAdapter.createSession(any())).thenReturn(mockSttSession);

        sessionWithMemory.onAudioStreamStart("stream-1");

        org.mockito.Mockito.verify(memoryService).startEpisode("mem-session", "default-user");
    }

    @Test
    void onAudioStreamStartNoExceptionWhenMemoryServiceNull() {
        SttAdapter.SttSession mockSttSession = mock(SttAdapter.SttSession.class);
        when(sttAdapter.createSession(any())).thenReturn(mockSttSession);

        // session has null memoryService (from setUp)
        session.onAudioStreamStart("stream-1");

        assertThat(session.currentState()).isEqualTo(AgentState.LISTENING);
    }

    @Test
    void closeEndsEpisodeWhenMemoryServicePresent() {
        com.pairion.memory.service.MemoryService memoryService =
                mock(com.pairion.memory.service.MemoryService.class);
        AgentSession sessionWithMemory =
                new AgentSession(
                        "close-session",
                        sttAdapter,
                        llmAdapter,
                        ttsAdapter,
                        soulProvider,
                        toolDispatcher,
                        null,
                        null,
                        null,
                        memoryService,
                        events::add);

        sessionWithMemory.close();

        org.mockito.Mockito.verify(memoryService).endEpisode("close-session");
    }

    @Test
    void closeNoExceptionWhenMemoryServiceNull() {
        // session from setUp has null memoryService
        session.close();
        // No exception expected
    }

    @Test
    @SuppressWarnings("unchecked")
    void handleSttFinalEventCallsRecordTurnWhenMemoryServicePresent() {
        com.pairion.memory.service.MemoryService memoryService =
                mock(com.pairion.memory.service.MemoryService.class);

        Consumer<SttEvent>[] sttConsumer = new Consumer[1];
        SttAdapter.SttSession mockSttSession = mock(SttAdapter.SttSession.class);
        when(sttAdapter.createSession(any()))
                .thenAnswer(
                        inv -> {
                            sttConsumer[0] = inv.getArgument(0);
                            return mockSttSession;
                        });

        when(soulProvider.getSystemPrompt(eq("mem2-session"), anyString(), anyString()))
                .thenReturn("You are Jarvis.");
        doAnswer(
                        inv -> {
                            Consumer<LlmEvent> consumer = inv.getArgument(1);
                            consumer.accept(new LlmEvent.Stop(1));
                            return null;
                        })
                .when(llmAdapter)
                .generate(any(), any());

        AgentSession sessionWithMemory =
                new AgentSession(
                        "mem2-session",
                        sttAdapter,
                        llmAdapter,
                        ttsAdapter,
                        soulProvider,
                        toolDispatcher,
                        null,
                        null,
                        null,
                        memoryService,
                        events::add);

        sessionWithMemory.onAudioStreamStart("stream-mem2");

        doAnswer(
                        inv -> {
                            sttConsumer[0].accept(new SttEvent.Final("hello memory", 500));
                            return null;
                        })
                .when(mockSttSession)
                .finalizeStream();

        sessionWithMemory.onSpeechEnded();

        org.mockito.Mockito.verify(memoryService)
                .recordTurn("mem2-session", "user", "hello memory");
    }

    @Test
    @SuppressWarnings("unchecked")
    void synthesizeSpeechCallsRecordTurnForAssistantWhenMemoryServicePresent() {
        com.pairion.memory.service.MemoryService memoryService =
                mock(com.pairion.memory.service.MemoryService.class);

        Consumer<SttEvent>[] sttConsumer = new Consumer[1];
        SttAdapter.SttSession mockSttSession = mock(SttAdapter.SttSession.class);
        when(sttAdapter.createSession(any()))
                .thenAnswer(
                        inv -> {
                            sttConsumer[0] = inv.getArgument(0);
                            return mockSttSession;
                        });

        // TTS must be available so synthesizeSpeech is invoked
        when(ttsAdapter.capabilities()).thenReturn(new TtsCapabilities(true, true));

        when(soulProvider.getSystemPrompt(eq("tts-mem-session"), anyString(), anyString()))
                .thenReturn("You are Jarvis.");
        doAnswer(
                        inv -> {
                            Consumer<LlmEvent> consumer = inv.getArgument(1);
                            consumer.accept(new LlmEvent.TokenDelta("Hello from assistant"));
                            consumer.accept(new LlmEvent.Stop(1));
                            return null;
                        })
                .when(llmAdapter)
                .generate(any(), any());

        AgentSession sessionWithMemory =
                new AgentSession(
                        "tts-mem-session",
                        sttAdapter,
                        llmAdapter,
                        ttsAdapter,
                        soulProvider,
                        toolDispatcher,
                        null,
                        null,
                        null,
                        memoryService,
                        events::add);

        sessionWithMemory.onAudioStreamStart("stream-tts-mem");

        doAnswer(
                        inv -> {
                            sttConsumer[0].accept(new SttEvent.Final("hello", 500));
                            return null;
                        })
                .when(mockSttSession)
                .finalizeStream();

        sessionWithMemory.onSpeechEnded();

        org.mockito.Mockito.verify(memoryService)
                .recordTurn("tts-mem-session", "assistant", "Hello from assistant");
    }
}
