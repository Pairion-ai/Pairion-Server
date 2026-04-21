package com.pairion.agent.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.pairion.adapters.llm.spi.LlmAdapter;
import com.pairion.adapters.stt.spi.SttAdapter;
import com.pairion.adapters.tts.spi.TtsAdapter;
import com.pairion.agent.soul.SoulPromptProvider;
import com.pairion.agent.tools.ToolDispatcher;
import com.pairion.core.llm.LlmEvent;
import com.pairion.core.stt.SttEvent;
import com.pairion.core.tts.TtsCapabilities;
import com.pairion.core.tts.TtsEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/**
 * Tests verifying that {@link AgentSession} emits {@code [LATENCY]} log lines for each stage
 * of the voice turn loop, for both tool-use and no-tool turns.
 */
class AgentSessionLatencyTest {

    private SttAdapter sttAdapter;
    private LlmAdapter llmAdapter;
    private TtsAdapter ttsAdapter;
    private SoulPromptProvider soulProvider;
    private ToolDispatcher toolDispatcher;
    private AgentSession session;

    private Logger agentSessionLogger;
    private ListAppender<ILoggingEvent> listAppender;

    @BeforeEach
    void setUp() {
        sttAdapter = mock(SttAdapter.class);
        llmAdapter = mock(LlmAdapter.class);
        ttsAdapter = mock(TtsAdapter.class);
        soulProvider = mock(SoulPromptProvider.class);
        toolDispatcher = mock(ToolDispatcher.class);

        // TTS available for all latency tests (override per-test if needed)
        when(ttsAdapter.capabilities()).thenReturn(new TtsCapabilities(true, true));
        when(soulProvider.getSystemPrompt(any())).thenReturn("You are Alfred.");

        session =
                new AgentSession(
                        "latency-session",
                        sttAdapter,
                        llmAdapter,
                        ttsAdapter,
                        soulProvider,
                        toolDispatcher,
                        event -> {});

        // Attach Logback ListAppender to capture AgentSession log output
        agentSessionLogger = (Logger) LoggerFactory.getLogger(AgentSession.class);
        agentSessionLogger.setLevel(Level.INFO);
        listAppender = new ListAppender<>();
        listAppender.start();
        agentSessionLogger.addAppender(listAppender);
    }

    @AfterEach
    void tearDown() {
        agentSessionLogger.detachAppender(listAppender);
    }

    /**
     * Verifies that a full turn without tool use emits [LATENCY] log lines for stages A, B, E, F,
     * and a summary line showing C=N/A and D=N/A.
     */
    @Test
    @SuppressWarnings("unchecked")
    void latencyLogsEmittedForTurnWithoutToolUse() {
        Consumer<SttEvent>[] sttConsumer = new Consumer[1];
        SttAdapter.SttSession mockSttSession = mock(SttAdapter.SttSession.class);
        when(sttAdapter.createSession(any()))
                .thenAnswer(
                        inv -> {
                            sttConsumer[0] = inv.getArgument(0);
                            return mockSttSession;
                        });

        // LLM returns text immediately (no tool call)
        doAnswer(
                        inv -> {
                            Consumer<LlmEvent> consumer = inv.getArgument(1);
                            consumer.accept(new LlmEvent.TokenDelta("The answer is yes."));
                            consumer.accept(new LlmEvent.Stop(3));
                            return null;
                        })
                .when(llmAdapter)
                .generate(any(), any());

        // TTS emits two chunks (covers both first-chunk and subsequent-chunk code paths)
        // and a completion event
        doAnswer(
                        inv -> {
                            Consumer<TtsEvent> consumer = inv.getArgument(1);
                            consumer.accept(new TtsEvent.Chunk(new byte[] {0x01, 0x02}, true));
                            consumer.accept(new TtsEvent.Chunk(new byte[] {0x03, 0x04}, false));
                            consumer.accept(new TtsEvent.Completed(200L));
                            return null;
                        })
                .when(ttsAdapter)
                .speak(any(), any());

        // Wire finalizeStream to emit Final event so onSpeechEnded() sets sttStartNano
        doAnswer(
                        inv -> {
                            sttConsumer[0].accept(
                                    new SttEvent.Final("Is that right?", 800));
                            return null;
                        })
                .when(mockSttSession)
                .finalizeStream();

        session.onAudioStreamStart("stream-1");
        session.onSpeechEnded();

        List<String> messages = capturedMessages();

        // Stage A must be logged
        assertThat(messages).anyMatch(m -> m.contains("[LATENCY] Stage A (STT):"));

        // Stage B must be logged
        assertThat(messages).anyMatch(m -> m.contains("[LATENCY] Stage B (LLM-1):"));

        // Stage C and D must NOT be logged as individual lines (no tool)
        assertThat(messages).noneMatch(m -> m.contains("[LATENCY] Stage C (Tool):"));
        assertThat(messages).noneMatch(m -> m.contains("[LATENCY] Stage D (LLM-2):"));

        // Stage E must be logged
        assertThat(messages).anyMatch(m -> m.contains("[LATENCY] Stage E (TTS):"));

        // Stage F must be logged
        assertThat(messages).anyMatch(m -> m.contains("[LATENCY] Stage F (Send):"));

        // Summary line must be present with C=N/A and D=N/A
        assertThat(messages)
                .anyMatch(
                        m ->
                                m.contains("[LATENCY] Total (SpeechEnded")
                                        && m.contains("C=N/A")
                                        && m.contains("D=N/A"));
    }

    /**
     * Verifies that a full turn with tool use emits [LATENCY] log lines for all stages A–F and
     * a summary line showing numeric values for C and D.
     */
    @Test
    @SuppressWarnings("unchecked")
    void latencyLogsEmittedForTurnWithToolUse() {
        Consumer<SttEvent>[] sttConsumer = new Consumer[1];
        SttAdapter.SttSession mockSttSession = mock(SttAdapter.SttSession.class);
        when(sttAdapter.createSession(any()))
                .thenAnswer(
                        inv -> {
                            sttConsumer[0] = inv.getArgument(0);
                            return mockSttSession;
                        });

        // LLM round 1: emits a tool call request
        // LLM round 2: emits text response
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
                                consumer.accept(
                                        new LlmEvent.TokenDelta("It is seventy degrees in Dallas."));
                                consumer.accept(new LlmEvent.Stop(6));
                            }
                            return null;
                        })
                .when(llmAdapter)
                .generate(any(), any());

        when(toolDispatcher.dispatch("get_current_weather", Map.of("city", "Dallas")))
                .thenReturn(Map.of("temperature_f", 70.0, "conditions", "Clear"));

        // TTS emits two chunks to cover both first and subsequent chunk paths
        doAnswer(
                        inv -> {
                            Consumer<TtsEvent> consumer = inv.getArgument(1);
                            consumer.accept(new TtsEvent.Chunk(new byte[] {0x10, 0x11}, true));
                            consumer.accept(new TtsEvent.Chunk(new byte[] {0x12, 0x13}, false));
                            consumer.accept(new TtsEvent.Completed(300L));
                            return null;
                        })
                .when(ttsAdapter)
                .speak(any(), any());

        doAnswer(
                        inv -> {
                            sttConsumer[0].accept(
                                    new SttEvent.Final("What is the weather in Dallas?", 900));
                            return null;
                        })
                .when(mockSttSession)
                .finalizeStream();

        session.onAudioStreamStart("stream-1");
        session.onSpeechEnded();

        List<String> messages = capturedMessages();

        // All individual stage lines must be present
        assertThat(messages).anyMatch(m -> m.contains("[LATENCY] Stage A (STT):"));
        assertThat(messages).anyMatch(m -> m.contains("[LATENCY] Stage B (LLM-1):"));
        assertThat(messages).anyMatch(m -> m.contains("[LATENCY] Stage C (Tool):"));
        assertThat(messages).anyMatch(m -> m.contains("[LATENCY] Stage D (LLM-2):"));
        assertThat(messages).anyMatch(m -> m.contains("[LATENCY] Stage E (TTS):"));
        assertThat(messages).anyMatch(m -> m.contains("[LATENCY] Stage F (Send):"));

        // Summary line must be present — with numeric C and D (not N/A)
        assertThat(messages)
                .anyMatch(
                        m ->
                                m.contains("[LATENCY] Total (SpeechEnded")
                                        && m.contains("C=")
                                        && !m.contains("C=N/A")
                                        && m.contains("D=")
                                        && !m.contains("D=N/A"));
    }

    /**
     * Verifies the exact format of the summary log line for a no-tool turn: it must contain
     * the required stage labels A=, B=, C=N/A, D=N/A, E=, F=.
     */
    @Test
    @SuppressWarnings("unchecked")
    void summaryLineContainsAllExpectedStageLabels() {
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
                            consumer.accept(new LlmEvent.TokenDelta("Hello."));
                            consumer.accept(new LlmEvent.Stop(1));
                            return null;
                        })
                .when(llmAdapter)
                .generate(any(), any());

        doAnswer(
                        inv -> {
                            Consumer<TtsEvent> consumer = inv.getArgument(1);
                            consumer.accept(new TtsEvent.Chunk(new byte[] {0x01}, true));
                            consumer.accept(new TtsEvent.Completed(50L));
                            return null;
                        })
                .when(ttsAdapter)
                .speak(any(), any());

        doAnswer(
                        inv -> {
                            sttConsumer[0].accept(new SttEvent.Final("Hello", 300));
                            return null;
                        })
                .when(mockSttSession)
                .finalizeStream();

        session.onAudioStreamStart("stream-1");
        session.onSpeechEnded();

        // Find the summary line
        List<String> messages = capturedMessages();
        String summaryLine =
                messages.stream()
                        .filter(m -> m.contains("[LATENCY] Total (SpeechEnded"))
                        .findFirst()
                        .orElse("");

        assertThat(summaryLine).isNotEmpty();
        assertThat(summaryLine).contains("A=");
        assertThat(summaryLine).contains("B=");
        assertThat(summaryLine).contains("C=N/A");
        assertThat(summaryLine).contains("D=N/A");
        assertThat(summaryLine).contains("E=");
        assertThat(summaryLine).contains("F=");
        assertThat(summaryLine).contains("ms | Stages:");
    }

    /** Extracts the formatted message strings from all captured log events. */
    private List<String> capturedMessages() {
        List<String> messages = new ArrayList<>();
        for (ILoggingEvent event : listAppender.list) {
            messages.add(event.getFormattedMessage());
        }
        return messages;
    }
}
