package com.pairion.adapters.llm.openaicompat;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Tests for {@link OpenAiCompatSseParser}. */
class OpenAiCompatSseParserTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    // ─── Helper records for capturing callback output ────────────────────────

    private record TokenCapture(String delta) {}

    private record ToolCallCapture(String id, String name, Map<String, Object> input) {}

    private static final class CapturingCallback implements OpenAiCompatSseParser.Callback {
        final List<TokenCapture> tokens = new ArrayList<>();
        final List<ToolCallCapture> toolCalls = new ArrayList<>();
        int doneOutputTokens = -1;

        @Override
        public void onToken(String delta) {
            tokens.add(new TokenCapture(delta));
        }

        @Override
        public void onToolCallRequest(String id, String name, Map<String, Object> input) {
            toolCalls.add(new ToolCallCapture(id, name, input));
        }

        @Override
        public void onDone(int outputTokens) {
            doneOutputTokens = outputTokens;
        }
    }

    // ─── Text streaming tests ─────────────────────────────────────────────────

    @Test
    void parsesTextTokenDelta() {
        OpenAiCompatSseParser parser = new OpenAiCompatSseParser(objectMapper);
        CapturingCallback cb = new CapturingCallback();

        parser.parseLine(
                "data: {\"choices\":[{\"delta\":{\"content\":\"Hello\"},\"finish_reason\":null}]}",
                cb);

        assertThat(cb.tokens).hasSize(1);
        assertThat(cb.tokens.get(0).delta()).isEqualTo("Hello");
        assertThat(cb.toolCalls).isEmpty();
        assertThat(cb.doneOutputTokens).isEqualTo(-1);
    }

    @Test
    void parsesMultipleTextTokensInSequence() {
        OpenAiCompatSseParser parser = new OpenAiCompatSseParser(objectMapper);
        CapturingCallback cb = new CapturingCallback();

        parser.parseLine(
                "data: {\"choices\":[{\"delta\":{\"content\":\"Hello\"},\"finish_reason\":null}]}",
                cb);
        parser.parseLine(
                "data: {\"choices\":[{\"delta\":{\"content\":\" world\"},\"finish_reason\":null}]}",
                cb);

        assertThat(cb.tokens).hasSize(2);
        assertThat(cb.tokens.get(0).delta()).isEqualTo("Hello");
        assertThat(cb.tokens.get(1).delta()).isEqualTo(" world");
    }

    @Test
    void doneSentinelCallsOnDone() {
        OpenAiCompatSseParser parser = new OpenAiCompatSseParser(objectMapper);
        CapturingCallback cb = new CapturingCallback();

        parser.parseLine("data: [DONE]", cb);

        assertThat(cb.doneOutputTokens).isEqualTo(0);
    }

    @Test
    void extractsOutputTokensFromUsageBeforeDone() {
        OpenAiCompatSseParser parser = new OpenAiCompatSseParser(objectMapper);
        CapturingCallback cb = new CapturingCallback();

        // Last data chunk contains usage
        parser.parseLine(
                "data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}],"
                        + "\"usage\":{\"completion_tokens\":42}}",
                cb);
        parser.parseLine("data: [DONE]", cb);

        assertThat(cb.doneOutputTokens).isEqualTo(42);
    }

    // ─── Empty / comment line handling ───────────────────────────────────────

    @Test
    void ignoresEmptyLines() {
        OpenAiCompatSseParser parser = new OpenAiCompatSseParser(objectMapper);
        CapturingCallback cb = new CapturingCallback();

        parser.parseLine("", cb);
        parser.parseLine("   ", cb);

        assertThat(cb.tokens).isEmpty();
        assertThat(cb.doneOutputTokens).isEqualTo(-1);
    }

    @Test
    void ignoresNullLine() {
        OpenAiCompatSseParser parser = new OpenAiCompatSseParser(objectMapper);
        CapturingCallback cb = new CapturingCallback();

        parser.parseLine(null, cb);

        assertThat(cb.tokens).isEmpty();
    }

    @Test
    void ignoresSseCommentLines() {
        OpenAiCompatSseParser parser = new OpenAiCompatSseParser(objectMapper);
        CapturingCallback cb = new CapturingCallback();

        parser.parseLine(": keep-alive", cb);

        assertThat(cb.tokens).isEmpty();
    }

    @Test
    void ignoresNonDataLines() {
        OpenAiCompatSseParser parser = new OpenAiCompatSseParser(objectMapper);
        CapturingCallback cb = new CapturingCallback();

        parser.parseLine("event: message", cb);

        assertThat(cb.tokens).isEmpty();
    }

    @Test
    void ignoresMalformedJsonGracefully() {
        OpenAiCompatSseParser parser = new OpenAiCompatSseParser(objectMapper);
        CapturingCallback cb = new CapturingCallback();

        // Should not throw; should log and continue
        parser.parseLine("data: not-valid-json", cb);

        assertThat(cb.tokens).isEmpty();
        assertThat(cb.doneOutputTokens).isEqualTo(-1);
    }

    // ─── Null content (tool-only chunks) ─────────────────────────────────────

    @Test
    void skipsNullContent() {
        OpenAiCompatSseParser parser = new OpenAiCompatSseParser(objectMapper);
        CapturingCallback cb = new CapturingCallback();

        parser.parseLine(
                "data: {\"choices\":[{\"delta\":{\"content\":null},\"finish_reason\":null}]}",
                cb);

        assertThat(cb.tokens).isEmpty();
    }

    @Test
    void skipsEmptyContent() {
        OpenAiCompatSseParser parser = new OpenAiCompatSseParser(objectMapper);
        CapturingCallback cb = new CapturingCallback();

        parser.parseLine(
                "data: {\"choices\":[{\"delta\":{\"content\":\"\"},\"finish_reason\":null}]}",
                cb);

        assertThat(cb.tokens).isEmpty();
    }

    // ─── Tool call accumulation tests ────────────────────────────────────────

    @Test
    void parsesToolCallAcrossMultipleChunks() {
        OpenAiCompatSseParser parser = new OpenAiCompatSseParser(objectMapper);
        CapturingCallback cb = new CapturingCallback();

        // Chunk 1: id + function name + first argument fragment
        parser.parseLine(
                "data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"id\":\"call_xyz\","
                        + "\"function\":{\"name\":\"get_current_weather\",\"arguments\":\"{\\\"city\\\"\"}}]},"
                        + "\"finish_reason\":null}]}",
                cb);

        // Chunk 2: remaining argument fragment
        parser.parseLine(
                "data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,"
                        + "\"function\":{\"arguments\":\": \\\"Dallas\\\"}}\"}}]},"
                        + "\"finish_reason\":null}]}",
                cb);

        // Finish chunk: finish_reason = tool_calls → dispatch
        parser.parseLine(
                "data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"tool_calls\"}],"
                        + "\"usage\":{\"completion_tokens\":15}}",
                cb);

        assertThat(cb.toolCalls).hasSize(1);
        ToolCallCapture tc = cb.toolCalls.get(0);
        assertThat(tc.id()).isEqualTo("call_xyz");
        assertThat(tc.name()).isEqualTo("get_current_weather");
        assertThat(tc.input()).containsEntry("city", "Dallas");
        assertThat(cb.tokens).isEmpty();
    }

    @Test
    void parsesToolCallWithEmptyArguments() {
        OpenAiCompatSseParser parser = new OpenAiCompatSseParser(objectMapper);
        CapturingCallback cb = new CapturingCallback();

        parser.parseLine(
                "data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"id\":\"call_1\","
                        + "\"function\":{\"name\":\"get_time\",\"arguments\":\"\"}}]},"
                        + "\"finish_reason\":null}]}",
                cb);
        parser.parseLine(
                "data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"tool_calls\"}]}",
                cb);

        assertThat(cb.toolCalls).hasSize(1);
        assertThat(cb.toolCalls.get(0).id()).isEqualTo("call_1");
        assertThat(cb.toolCalls.get(0).name()).isEqualTo("get_time");
        assertThat(cb.toolCalls.get(0).input()).isEmpty();
    }

    @Test
    void parsesMultipleParallelToolCalls() {
        OpenAiCompatSseParser parser = new OpenAiCompatSseParser(objectMapper);
        CapturingCallback cb = new CapturingCallback();

        // First tool call at index 0
        parser.parseLine(
                "data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"id\":\"call_a\","
                        + "\"function\":{\"name\":\"tool_one\",\"arguments\":\"{\\\"x\\\":1}\"}}]},"
                        + "\"finish_reason\":null}]}",
                cb);

        // Second tool call at index 1
        parser.parseLine(
                "data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":1,\"id\":\"call_b\","
                        + "\"function\":{\"name\":\"tool_two\",\"arguments\":\"{\\\"y\\\":2}\"}}]},"
                        + "\"finish_reason\":null}]}",
                cb);

        // Dispatch both
        parser.parseLine(
                "data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"tool_calls\"}]}",
                cb);

        assertThat(cb.toolCalls).hasSize(2);
        List<String> ids = cb.toolCalls.stream().map(ToolCallCapture::id).toList();
        assertThat(ids).containsExactlyInAnyOrder("call_a", "call_b");
    }

    // ─── Full text turn end-to-end ────────────────────────────────────────────

    @Test
    void fullTextTurnEndToEnd() {
        OpenAiCompatSseParser parser = new OpenAiCompatSseParser(objectMapper);
        CapturingCallback cb = new CapturingCallback();

        parser.parseLine(
                "data: {\"choices\":[{\"delta\":{\"content\":\"It is \"},\"finish_reason\":null}]}",
                cb);
        parser.parseLine(
                "data: {\"choices\":[{\"delta\":{\"content\":\"sunny.\"},\"finish_reason\":null}]}",
                cb);
        parser.parseLine(
                "data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}],"
                        + "\"usage\":{\"completion_tokens\":7}}",
                cb);
        parser.parseLine("data: [DONE]", cb);

        assertThat(cb.tokens).hasSize(2);
        assertThat(cb.tokens.get(0).delta()).isEqualTo("It is ");
        assertThat(cb.tokens.get(1).delta()).isEqualTo("sunny.");
        assertThat(cb.toolCalls).isEmpty();
        assertThat(cb.doneOutputTokens).isEqualTo(7);
    }

    // ─── Choices missing / empty ──────────────────────────────────────────────

    @Test
    void handlesChunkWithNoChoices() {
        OpenAiCompatSseParser parser = new OpenAiCompatSseParser(objectMapper);
        CapturingCallback cb = new CapturingCallback();

        // Chunk with usage but no choices (can appear at end of some providers)
        parser.parseLine(
                "data: {\"usage\":{\"completion_tokens\":3}}", cb);
        parser.parseLine("data: [DONE]", cb);

        assertThat(cb.tokens).isEmpty();
        assertThat(cb.doneOutputTokens).isEqualTo(3);
    }

    @Test
    void handlesChunkWithEmptyChoicesArray() {
        OpenAiCompatSseParser parser = new OpenAiCompatSseParser(objectMapper);
        CapturingCallback cb = new CapturingCallback();

        parser.parseLine("data: {\"choices\":[]}", cb);
        parser.parseLine("data: [DONE]", cb);

        assertThat(cb.tokens).isEmpty();
        assertThat(cb.doneOutputTokens).isEqualTo(0);
    }

    // ─── Tool call edge cases: ID/name arrival in later chunks ────────────────

    @Test
    void toolCallIdArrivesInLaterChunk() {
        // First chunk: no id field — acc created with empty id
        // Second chunk: id field present — setId() called
        OpenAiCompatSseParser parser = new OpenAiCompatSseParser(objectMapper);
        CapturingCallback cb = new CapturingCallback();

        // First chunk: no "id", but has function name + empty arguments
        parser.parseLine(
                "data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,"
                        + "\"function\":{\"name\":\"get_time\",\"arguments\":\"{}\"}}]},"
                        + "\"finish_reason\":null}]}",
                cb);

        // Second chunk: id arrives
        parser.parseLine(
                "data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,"
                        + "\"id\":\"call_late_id\"}]},"
                        + "\"finish_reason\":null}]}",
                cb);

        // Dispatch
        parser.parseLine(
                "data: {\"choices\":[{\"delta\":{},"
                        + "\"finish_reason\":\"tool_calls\"}]}",
                cb);

        assertThat(cb.toolCalls).hasSize(1);
        assertThat(cb.toolCalls.get(0).id()).isEqualTo("call_late_id");
        assertThat(cb.toolCalls.get(0).name()).isEqualTo("get_time");
    }

    @Test
    void toolCallNameArrivesInLaterChunk() {
        // First chunk: has id, no function field — acc created with empty name
        // Second chunk: function.name arrives — setName() called
        OpenAiCompatSseParser parser = new OpenAiCompatSseParser(objectMapper);
        CapturingCallback cb = new CapturingCallback();

        // First chunk: id present, no function field at all
        parser.parseLine(
                "data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,"
                        + "\"id\":\"call_abc\"}]},"
                        + "\"finish_reason\":null}]}",
                cb);

        // Second chunk: function name + arguments arrive
        parser.parseLine(
                "data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,"
                        + "\"function\":{\"name\":\"get_date\",\"arguments\":\"{}\"}}]},"
                        + "\"finish_reason\":null}]}",
                cb);

        // Dispatch
        parser.parseLine(
                "data: {\"choices\":[{\"delta\":{},"
                        + "\"finish_reason\":\"tool_calls\"}]}",
                cb);

        assertThat(cb.toolCalls).hasSize(1);
        assertThat(cb.toolCalls.get(0).id()).isEqualTo("call_abc");
        assertThat(cb.toolCalls.get(0).name()).isEqualTo("get_date");
    }

    @Test
    void toolCallFunctionFieldWithoutNameInFirstChunk() {
        // First chunk: has function field but no "name" key — exercises fnNodeForLambda.has("name") false branch
        OpenAiCompatSseParser parser = new OpenAiCompatSseParser(objectMapper);
        CapturingCallback cb = new CapturingCallback();

        // First chunk: function has no "name", only arguments
        parser.parseLine(
                "data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,"
                        + "\"id\":\"call_nofn\","
                        + "\"function\":{\"arguments\":\"{\\\"x\\\":1}\"}}]},"
                        + "\"finish_reason\":null}]}",
                cb);

        // Second chunk: name arrives
        parser.parseLine(
                "data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,"
                        + "\"function\":{\"name\":\"compute\"}}]},"
                        + "\"finish_reason\":null}]}",
                cb);

        parser.parseLine(
                "data: {\"choices\":[{\"delta\":{},"
                        + "\"finish_reason\":\"tool_calls\"}]}",
                cb);

        assertThat(cb.toolCalls).hasSize(1);
        assertThat(cb.toolCalls.get(0).id()).isEqualTo("call_nofn");
        assertThat(cb.toolCalls.get(0).name()).isEqualTo("compute");
    }

    @Test
    void toolCallFunctionChunkWithNoArgumentsField() {
        // A tool_calls chunk that has a function object but no "arguments" key
        // exercises the fnNode.has("arguments") false branch
        OpenAiCompatSseParser parser = new OpenAiCompatSseParser(objectMapper);
        CapturingCallback cb = new CapturingCallback();

        // First chunk: function has name only, no arguments
        parser.parseLine(
                "data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,"
                        + "\"id\":\"call_noargs\","
                        + "\"function\":{\"name\":\"ping\"}}]},"
                        + "\"finish_reason\":null}]}",
                cb);

        // Second chunk: arguments arrive
        parser.parseLine(
                "data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,"
                        + "\"function\":{\"arguments\":\"{}\"}}]},"
                        + "\"finish_reason\":null}]}",
                cb);

        parser.parseLine(
                "data: {\"choices\":[{\"delta\":{},"
                        + "\"finish_reason\":\"tool_calls\"}]}",
                cb);

        assertThat(cb.toolCalls).hasSize(1);
        assertThat(cb.toolCalls.get(0).name()).isEqualTo("ping");
    }

    @Test
    void dispatchToolCallWithMalformedJsonFallsBackToEmptyInput() {
        // Accumulated arguments that are not valid JSON — dispatchAllToolCalls error path
        OpenAiCompatSseParser parser = new OpenAiCompatSseParser(objectMapper);
        CapturingCallback cb = new CapturingCallback();

        // Arguments accumulate to malformed JSON
        parser.parseLine(
                "data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,"
                        + "\"id\":\"call_bad\","
                        + "\"function\":{\"name\":\"bad_tool\","
                        + "\"arguments\":\"{not valid json\"}}]},"
                        + "\"finish_reason\":null}]}",
                cb);

        parser.parseLine(
                "data: {\"choices\":[{\"delta\":{},"
                        + "\"finish_reason\":\"tool_calls\"}]}",
                cb);

        // Should still deliver the tool call, but with empty input (fallback)
        assertThat(cb.toolCalls).hasSize(1);
        assertThat(cb.toolCalls.get(0).id()).isEqualTo("call_bad");
        assertThat(cb.toolCalls.get(0).name()).isEqualTo("bad_tool");
        assertThat(cb.toolCalls.get(0).input()).isEmpty();
    }

    @Test
    void usageNodePresentButMissingCompletionTokensKey() {
        // usage object exists but has no "completion_tokens" key — exercises the false branch
        // of usageNode.has("completion_tokens")
        OpenAiCompatSseParser parser = new OpenAiCompatSseParser(objectMapper);
        CapturingCallback cb = new CapturingCallback();

        parser.parseLine(
                "data: {\"choices\":[{\"delta\":{},"
                        + "\"finish_reason\":\"stop\"}],"
                        + "\"usage\":{\"prompt_tokens\":10}}",
                cb);
        parser.parseLine("data: [DONE]", cb);

        // outputTokens stays at 0 (default) since completion_tokens was absent
        assertThat(cb.doneOutputTokens).isEqualTo(0);
    }

    // ─── Defensive branch coverage ────────────────────────────────────────────

    @Test
    void choiceWithoutDeltaFieldIsIgnored() {
        // Exercises the delta == null branch — some providers emit role-only chunks
        OpenAiCompatSseParser parser = new OpenAiCompatSseParser(objectMapper);
        CapturingCallback cb = new CapturingCallback();

        // Choice has no "delta" field at all
        parser.parseLine(
                "data: {\"choices\":[{\"role\":\"assistant\",\"finish_reason\":null}]}",
                cb);

        assertThat(cb.tokens).isEmpty();
        assertThat(cb.toolCalls).isEmpty();
    }

    @Test
    void toolCallsValueIsNotArrayIsIgnored() {
        // Exercises toolCallsNode.isArray() == false branch (malformed/unexpected format)
        OpenAiCompatSseParser parser = new OpenAiCompatSseParser(objectMapper);
        CapturingCallback cb = new CapturingCallback();

        // tool_calls is an object, not an array
        parser.parseLine(
                "data: {\"choices\":[{\"delta\":{\"tool_calls\":{}},"
                        + "\"finish_reason\":null}]}",
                cb);

        assertThat(cb.toolCalls).isEmpty();
    }

    @Test
    void toolCallsEntryWithoutIndexFieldDefaultsToZero() {
        // Exercises the tcNode.has("index") false branch — index defaults to 0
        OpenAiCompatSseParser parser = new OpenAiCompatSseParser(objectMapper);
        CapturingCallback cb = new CapturingCallback();

        // tool_calls entry has no "index" field
        parser.parseLine(
                "data: {\"choices\":[{\"delta\":{\"tool_calls\":[{"
                        + "\"id\":\"call_noindex\","
                        + "\"function\":{\"name\":\"no_idx_tool\",\"arguments\":\"{}\"}}]},"
                        + "\"finish_reason\":null}]}",
                cb);
        parser.parseLine(
                "data: {\"choices\":[{\"delta\":{},"
                        + "\"finish_reason\":\"tool_calls\"}]}",
                cb);

        assertThat(cb.toolCalls).hasSize(1);
        assertThat(cb.toolCalls.get(0).id()).isEqualTo("call_noindex");
        assertThat(cb.toolCalls.get(0).name()).isEqualTo("no_idx_tool");
    }

    @Test
    void chunkWithoutFinishReasonKeyIsHandled() {
        // Exercises finishReasonNode == null branch — "finish_reason" key entirely absent
        OpenAiCompatSseParser parser = new OpenAiCompatSseParser(objectMapper);
        CapturingCallback cb = new CapturingCallback();

        // No "finish_reason" key in choice — differs from "finish_reason": null
        parser.parseLine(
                "data: {\"choices\":[{\"delta\":{\"content\":\"Hello\"}}]}",
                cb);

        assertThat(cb.tokens).hasSize(1);
        assertThat(cb.tokens.get(0).delta()).isEqualTo("Hello");
        assertThat(cb.doneOutputTokens).isEqualTo(-1);
    }
}
