package com.pairion.adapters.llm.openaicompat;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pairion.core.llm.LlmRequest;
import com.pairion.core.llm.ToolDefinition;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Contract test for {@link DefaultOpenAiCompatClientWrapper}.
 *
 * <p>Uses an in-process {@link com.sun.net.httpserver.HttpServer} to serve pre-built SSE responses.
 * Verifies that the full HTTP → SSE parsing → callback chain works end-to-end without mocking any
 * internals.
 */
class OpenAiCompatContractTest {

    private HttpServer server;
    private String baseUrl;
    private volatile HttpHandler currentHandler;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/v1/chat/completions",
                exchange -> {
                    if (currentHandler != null) {
                        currentHandler.handle(exchange);
                    }
                });
        server.setExecutor(Executors.newSingleThreadExecutor());
        server.start();
        int port = server.getAddress().getPort();
        baseUrl = "http://localhost:" + port + "/v1";
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    /**
     * Writes a single SSE event to the exchange output stream. The event consists of a {@code data:}
     * prefixed line followed by a blank line as required by the SSE specification.
     */
    private void writeSseLine(OutputStream out, String dataLine) throws IOException {
        String event = dataLine + "\n\n";
        out.write(event.getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    // ─── Text-only turn ───────────────────────────────────────────────────────

    @Test
    void textOnlyTurnDeliversTokensAndStop() throws Exception {
        currentHandler =
                exchange -> {
                    exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
                    exchange.sendResponseHeaders(200, 0);
                    try (OutputStream out = exchange.getResponseBody()) {
                        writeSseLine(
                                out,
                                "data: {\"choices\":[{\"delta\":{\"content\":\"Sunny\"},"
                                        + "\"finish_reason\":null}]}");
                        writeSseLine(
                                out,
                                "data: {\"choices\":[{\"delta\":{\"content\":\" and warm.\"},"
                                        + "\"finish_reason\":null}]}");
                        writeSseLine(
                                out,
                                "data: {\"choices\":[{\"delta\":{},"
                                        + "\"finish_reason\":\"stop\"}],"
                                        + "\"usage\":{\"completion_tokens\":4}}");
                        writeSseLine(out, "data: [DONE]");
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                };

        DefaultOpenAiCompatClientWrapper wrapper =
                new DefaultOpenAiCompatClientWrapper(
                        HttpClient.newBuilder()
                                .followRedirects(HttpClient.Redirect.ALWAYS)
                                .build(),
                        new ObjectMapper());

        List<String> tokens = new ArrayList<>();
        AtomicInteger completedTokens = new AtomicInteger(-1);
        CountDownLatch latch = new CountDownLatch(1);

        wrapper.streamCompletion(
                baseUrl,
                "",
                "test-model",
                "You are a test.",
                "What is the weather?",
                List.of(),
                List.of(),
                new OpenAiCompatClientWrapper.StreamCallback() {
                    @Override
                    public void onToken(String delta) {
                        tokens.add(delta);
                    }

                    @Override
                    public void onToolCallRequest(String id, String name, Map<String, Object> input) {}

                    @Override
                    public void onComplete(int outputTokens) {
                        completedTokens.set(outputTokens);
                        latch.countDown();
                    }

                    @Override
                    public void onError(Exception e) {
                        latch.countDown();
                    }
                });

        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(tokens).containsExactly("Sunny", " and warm.");
        assertThat(completedTokens.get()).isEqualTo(4);
    }

    // ─── Tool call turn ───────────────────────────────────────────────────────

    @Test
    void toolCallTurnDeliversToolCallRequest() throws Exception {
        currentHandler =
                exchange -> {
                    exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
                    exchange.sendResponseHeaders(200, 0);
                    try (OutputStream out = exchange.getResponseBody()) {
                        // Chunk 1: function name + partial arguments
                        writeSseLine(
                                out,
                                "data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,"
                                        + "\"id\":\"call_test\","
                                        + "\"function\":{\"name\":\"get_current_weather\","
                                        + "\"arguments\":\"{\\\"city\\\"\"}}]},"
                                        + "\"finish_reason\":null}]}");
                        // Chunk 2: remaining arguments
                        writeSseLine(
                                out,
                                "data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,"
                                        + "\"function\":{\"arguments\":\": \\\"Seattle\\\"}}\"}}]},"
                                        + "\"finish_reason\":null}]}");
                        // Finish chunk
                        writeSseLine(
                                out,
                                "data: {\"choices\":[{\"delta\":{},"
                                        + "\"finish_reason\":\"tool_calls\"}],"
                                        + "\"usage\":{\"completion_tokens\":12}}");
                        writeSseLine(out, "data: [DONE]");
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                };

        DefaultOpenAiCompatClientWrapper wrapper =
                new DefaultOpenAiCompatClientWrapper(
                        HttpClient.newBuilder()
                                .followRedirects(HttpClient.Redirect.ALWAYS)
                                .build(),
                        new ObjectMapper());

        AtomicReference<String> toolId = new AtomicReference<>();
        AtomicReference<String> toolName = new AtomicReference<>();
        AtomicReference<Map<String, Object>> toolInput = new AtomicReference<>();
        AtomicInteger completedTokens = new AtomicInteger(-1);
        CountDownLatch latch = new CountDownLatch(1);

        wrapper.streamCompletion(
                baseUrl,
                "",
                "test-model",
                "You are a test.",
                "What is the weather in Seattle?",
                List.of(),
                List.of(),
                new OpenAiCompatClientWrapper.StreamCallback() {
                    @Override
                    public void onToken(String delta) {}

                    @Override
                    public void onToolCallRequest(String id, String name, Map<String, Object> input) {
                        toolId.set(id);
                        toolName.set(name);
                        toolInput.set(input);
                    }

                    @Override
                    public void onComplete(int outputTokens) {
                        completedTokens.set(outputTokens);
                        latch.countDown();
                    }

                    @Override
                    public void onError(Exception e) {
                        latch.countDown();
                    }
                });

        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(toolId.get()).isEqualTo("call_test");
        assertThat(toolName.get()).isEqualTo("get_current_weather");
        assertThat(toolInput.get()).containsEntry("city", "Seattle");
        assertThat(completedTokens.get()).isEqualTo(12);
    }

    // ─── HTTP error response ──────────────────────────────────────────────────

    @Test
    void httpErrorResponseCallsOnError() throws Exception {
        currentHandler =
                exchange -> {
                    byte[] body = "{\"error\":\"model not found\"}".getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(404, body.length);
                    try (OutputStream out = exchange.getResponseBody()) {
                        out.write(body);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                };

        DefaultOpenAiCompatClientWrapper wrapper =
                new DefaultOpenAiCompatClientWrapper(
                        HttpClient.newBuilder()
                                .followRedirects(HttpClient.Redirect.ALWAYS)
                                .build(),
                        new ObjectMapper());

        AtomicReference<Exception> caughtError = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        wrapper.streamCompletion(
                baseUrl,
                "",
                "unknown-model",
                "sys",
                "msg",
                List.of(),
                List.of(),
                new OpenAiCompatClientWrapper.StreamCallback() {
                    @Override
                    public void onToken(String delta) {}

                    @Override
                    public void onToolCallRequest(String id, String name, Map<String, Object> input) {}

                    @Override
                    public void onComplete(int outputTokens) {
                        latch.countDown();
                    }

                    @Override
                    public void onError(Exception e) {
                        caughtError.set(e);
                        latch.countDown();
                    }
                });

        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(caughtError.get()).isNotNull();
        assertThat(caughtError.get().getMessage()).contains("404");
    }

    // ─── API key forwarded in Authorization header ────────────────────────────

    @Test
    void apiKeyIsForwardedInAuthorizationHeader() throws Exception {
        AtomicReference<String> capturedAuth = new AtomicReference<>();
        currentHandler =
                (HttpExchange exchange) -> {
                    capturedAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
                    exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
                    exchange.sendResponseHeaders(200, 0);
                    try (OutputStream out = exchange.getResponseBody()) {
                        writeSseLine(
                                out,
                                "data: {\"choices\":[{\"delta\":{},"
                                        + "\"finish_reason\":\"stop\"}],"
                                        + "\"usage\":{\"completion_tokens\":1}}");
                        writeSseLine(out, "data: [DONE]");
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                };

        DefaultOpenAiCompatClientWrapper wrapper =
                new DefaultOpenAiCompatClientWrapper(
                        HttpClient.newBuilder()
                                .followRedirects(HttpClient.Redirect.ALWAYS)
                                .build(),
                        new ObjectMapper());

        CountDownLatch latch = new CountDownLatch(1);

        wrapper.streamCompletion(
                baseUrl,
                "my-secret-key",
                "test-model",
                "sys",
                "msg",
                List.of(),
                List.of(),
                new OpenAiCompatClientWrapper.StreamCallback() {
                    @Override
                    public void onToken(String delta) {}

                    @Override
                    public void onToolCallRequest(String id, String name, Map<String, Object> input) {}

                    @Override
                    public void onComplete(int outputTokens) {
                        latch.countDown();
                    }

                    @Override
                    public void onError(Exception e) {
                        latch.countDown();
                    }
                });

        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(capturedAuth.get()).isEqualTo("Bearer my-secret-key");
    }

    // ─── No auth header when apiKey is blank ─────────────────────────────────

    @Test
    void noAuthorizationHeaderWhenApiKeyIsBlank() throws Exception {
        AtomicReference<String> capturedAuth = new AtomicReference<>("__not_set__");
        currentHandler =
                (HttpExchange exchange) -> {
                    String auth = exchange.getRequestHeaders().getFirst("Authorization");
                    capturedAuth.set(auth);
                    exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
                    exchange.sendResponseHeaders(200, 0);
                    try (OutputStream out = exchange.getResponseBody()) {
                        writeSseLine(
                                out,
                                "data: {\"choices\":[{\"delta\":{},"
                                        + "\"finish_reason\":\"stop\"}]}");
                        writeSseLine(out, "data: [DONE]");
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                };

        DefaultOpenAiCompatClientWrapper wrapper =
                new DefaultOpenAiCompatClientWrapper(
                        HttpClient.newBuilder()
                                .followRedirects(HttpClient.Redirect.ALWAYS)
                                .build(),
                        new ObjectMapper());

        CountDownLatch latch = new CountDownLatch(1);

        wrapper.streamCompletion(
                baseUrl,
                "",
                "test-model",
                "sys",
                "msg",
                List.of(),
                List.of(),
                new OpenAiCompatClientWrapper.StreamCallback() {
                    @Override
                    public void onToken(String delta) {}

                    @Override
                    public void onToolCallRequest(String id, String name, Map<String, Object> input) {}

                    @Override
                    public void onComplete(int outputTokens) {
                        latch.countDown();
                    }

                    @Override
                    public void onError(Exception e) {
                        latch.countDown();
                    }
                });

        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        // null means the header was not present
        assertThat(capturedAuth.get()).isNull();
    }

    // ─── Request timeout ─────────────────────────────────────────────────────

    @Test
    void requestTimeoutCallsOnError() throws Exception {
        currentHandler =
                exchange -> {
                    // Simulate a backend that accepts the connection but never responds
                    // (e.g. a local model server still loading a model)
                    try {
                        Thread.sleep(5_000);
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                    }
                };

        DefaultOpenAiCompatClientWrapper wrapper =
                new DefaultOpenAiCompatClientWrapper(
                        HttpClient.newBuilder()
                                .followRedirects(HttpClient.Redirect.ALWAYS)
                                .build(),
                        new ObjectMapper(),
                        Duration.ofMillis(200));

        AtomicReference<Exception> caughtError = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        wrapper.streamCompletion(
                baseUrl,
                "",
                "test-model",
                "sys",
                "msg",
                List.of(),
                List.of(),
                new OpenAiCompatClientWrapper.StreamCallback() {
                    @Override
                    public void onToken(String delta) {}

                    @Override
                    public void onToolCallRequest(String id, String name, Map<String, Object> input) {}

                    @Override
                    public void onComplete(int outputTokens) {
                        latch.countDown();
                    }

                    @Override
                    public void onError(Exception e) {
                        caughtError.set(e);
                        latch.countDown();
                    }
                });

        assertThat(latch.await(3, TimeUnit.SECONDS)).isTrue();
        assertThat(caughtError.get()).isNotNull();
    }

    // ─── Tool history replayed in request body ────────────────────────────────

    @Test
    void toolCallHistoryIsIncludedInRequestBody() throws Exception {
        AtomicReference<String> capturedBody = new AtomicReference<>();
        currentHandler =
                (HttpExchange exchange) -> {
                    capturedBody.set(
                            new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                    exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
                    exchange.sendResponseHeaders(200, 0);
                    try (OutputStream out = exchange.getResponseBody()) {
                        writeSseLine(
                                out,
                                "data: {\"choices\":[{\"delta\":{\"content\":\"Yes.\"},"
                                        + "\"finish_reason\":null}]}");
                        writeSseLine(
                                out,
                                "data: {\"choices\":[{\"delta\":{},"
                                        + "\"finish_reason\":\"stop\"}],"
                                        + "\"usage\":{\"completion_tokens\":1}}");
                        writeSseLine(out, "data: [DONE]");
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                };

        DefaultOpenAiCompatClientWrapper wrapper =
                new DefaultOpenAiCompatClientWrapper(
                        HttpClient.newBuilder()
                                .followRedirects(HttpClient.Redirect.ALWAYS)
                                .build(),
                        new ObjectMapper());

        List<LlmRequest.ToolCallPair> history =
                List.of(
                        new LlmRequest.ToolCallPair(
                                "call_hist",
                                "get_current_weather",
                                Map.of("city", "Austin"),
                                Map.of("temperature_f", 78.0)));

        CountDownLatch latch = new CountDownLatch(1);

        wrapper.streamCompletion(
                baseUrl,
                "",
                "test-model",
                "sys",
                "What is the weather follow-up?",
                List.of(),
                history,
                new OpenAiCompatClientWrapper.StreamCallback() {
                    @Override
                    public void onToken(String delta) {}

                    @Override
                    public void onToolCallRequest(String id, String name, Map<String, Object> input) {}

                    @Override
                    public void onComplete(int outputTokens) {
                        latch.countDown();
                    }

                    @Override
                    public void onError(Exception e) {
                        latch.countDown();
                    }
                });

        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(capturedBody.get()).contains("call_hist");
        assertThat(capturedBody.get()).contains("get_current_weather");
        assertThat(capturedBody.get()).contains("Austin");
    }

    // ─── Tool definitions included in request body ────────────────────────────

    @Test
    void toolDefinitionsAreIncludedInRequestBody() throws Exception {
        AtomicReference<String> capturedBody = new AtomicReference<>();
        currentHandler =
                (HttpExchange exchange) -> {
                    capturedBody.set(
                            new String(
                                    exchange.getRequestBody().readAllBytes(),
                                    StandardCharsets.UTF_8));
                    exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
                    exchange.sendResponseHeaders(200, 0);
                    try (OutputStream out = exchange.getResponseBody()) {
                        writeSseLine(
                                out,
                                "data: {\"choices\":[{\"delta\":{\"content\":\"Done.\"},"
                                        + "\"finish_reason\":null}]}");
                        writeSseLine(
                                out,
                                "data: {\"choices\":[{\"delta\":{},"
                                        + "\"finish_reason\":\"stop\"}],"
                                        + "\"usage\":{\"completion_tokens\":1}}");
                        writeSseLine(out, "data: [DONE]");
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                };

        DefaultOpenAiCompatClientWrapper wrapper =
                new DefaultOpenAiCompatClientWrapper(
                        HttpClient.newBuilder()
                                .followRedirects(HttpClient.Redirect.ALWAYS)
                                .build(),
                        new ObjectMapper());

        List<ToolDefinition> tools =
                List.of(
                        new ToolDefinition(
                                "get_current_weather",
                                "Get the current weather for a city",
                                Map.of(
                                        "type",
                                        "object",
                                        "properties",
                                        Map.of("city", Map.of("type", "string")),
                                        "required",
                                        List.of("city"))));

        CountDownLatch latch = new CountDownLatch(1);

        wrapper.streamCompletion(
                baseUrl,
                "",
                "test-model",
                "sys",
                "What is the weather in Seattle?",
                tools,
                List.of(),
                new OpenAiCompatClientWrapper.StreamCallback() {
                    @Override
                    public void onToken(String delta) {}

                    @Override
                    public void onToolCallRequest(String id, String name, Map<String, Object> input) {}

                    @Override
                    public void onComplete(int outputTokens) {
                        latch.countDown();
                    }

                    @Override
                    public void onError(Exception e) {
                        latch.countDown();
                    }
                });

        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(capturedBody.get()).contains("get_current_weather");
        assertThat(capturedBody.get()).contains("Get the current weather for a city");
        assertThat(capturedBody.get()).contains("\"type\":\"function\"");
    }

    // ─── Streaming: first token arrives before server closes connection ───────

    /**
     * Verifies that the client processes SSE chunks as they arrive rather than buffering the full
     * response body. The server sends the first token chunk, then blocks waiting for the client to
     * signal receipt before sending the remaining chunks. If the client were buffering the full
     * body (e.g., via {@code BodyHandlers.ofString()}), this test would deadlock: the server would
     * never send the final chunks (causing no EOF), so the client's {@code send()} would never
     * return, and the client could never signal the server.
     */
    @Test
    void streamingDeliversFirstTokenBeforeConnectionCloses() throws Exception {
        CountDownLatch firstTokenReceived = new CountDownLatch(1);

        currentHandler =
                exchange -> {
                    exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
                    exchange.sendResponseHeaders(200, 0);
                    try (OutputStream out = exchange.getResponseBody()) {
                        // Send first token; client must receive this before we send the rest.
                        writeSseLine(
                                out,
                                "data: {\"choices\":[{\"delta\":{\"content\":\"Hi\"},"
                                        + "\"finish_reason\":null}]}");
                        // Wait for the client to signal it received the first token.
                        // With BodyHandlers.ofInputStream() the client processes chunks
                        // as they arrive, so this signal arrives quickly. With
                        // BodyHandlers.ofString() the client blocks in send() waiting
                        // for EOF that will never come (deadlock → test timeout).
                        firstTokenReceived.await(5, TimeUnit.SECONDS);
                        // Send the stop chunk and sentinel.
                        writeSseLine(
                                out,
                                "data: {\"choices\":[{\"delta\":{},"
                                        + "\"finish_reason\":\"stop\"}],"
                                        + "\"usage\":{\"completion_tokens\":1}}");
                        writeSseLine(out, "data: [DONE]");
                    } catch (IOException | InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                };

        DefaultOpenAiCompatClientWrapper wrapper =
                new DefaultOpenAiCompatClientWrapper(
                        HttpClient.newBuilder()
                                .followRedirects(HttpClient.Redirect.ALWAYS)
                                .build(),
                        new ObjectMapper());

        List<String> tokens = new ArrayList<>();
        AtomicInteger completedTokens = new AtomicInteger(-1);

        wrapper.streamCompletion(
                baseUrl,
                "",
                "test-model",
                "sys",
                "msg",
                List.of(),
                List.of(),
                new OpenAiCompatClientWrapper.StreamCallback() {
                    @Override
                    public void onToken(String delta) {
                        tokens.add(delta);
                        firstTokenReceived.countDown();
                    }

                    @Override
                    public void onToolCallRequest(String id, String name, Map<String, Object> input) {}

                    @Override
                    public void onComplete(int outputTokens) {
                        completedTokens.set(outputTokens);
                    }

                    @Override
                    public void onError(Exception e) {
                        firstTokenReceived.countDown(); // unblock server on error path
                    }
                });

        assertThat(firstTokenReceived.getCount()).isZero();
        assertThat(tokens).containsExactly("Hi");
        assertThat(completedTokens.get()).isEqualTo(1);
    }
}
