package dev.voiceagent.brain.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.service.TokenStream;
import dev.voiceagent.brain.agent.HrOnboardingAgent;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import jakarta.servlet.ServletOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * OpenAI-compatible {@code POST /v1/chat/completions}, always streamed as
 * server-sent events, backed by {@link HrOnboardingAgent}.
 *
 * Only {@code onPartialResponse} (final assistant text) is forwarded to the
 * SSE stream -- tool-call deltas and tool-execution results, which arrive
 * through separate TokenStream callbacks, never reach it. That's the
 * speech-only filtering TTS needs, essentially for free from the API shape.
 */
public final class ChatCompletionsHandler implements Handler {

    private static final Logger log = LoggerFactory.getLogger(ChatCompletionsHandler.class);

    private final HrOnboardingAgent agent;
    private final ObjectMapper objectMapper;

    public ChatCompletionsHandler(HrOnboardingAgent agent, ObjectMapper objectMapper) {
        this.agent = agent;
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(Context ctx) throws Exception {
        OpenAiDtos.ChatCompletionRequest request =
                objectMapper.readValue(ctx.body(), OpenAiDtos.ChatCompletionRequest.class);

        List<HrOnboardingAgent.Turn> history = request.messages().stream()
                .filter(m -> !"system".equals(m.role()))
                .map(m -> new HrOnboardingAgent.Turn(m.role(), m.content()))
                .toList();

        // LiveKit's very first generate_reply() call (the Milestone 4 greeting
        // trigger) arrives as a system-only message, which the filter above
        // empties out entirely. Fall back to that system content as a
        // synthetic user turn so the greeting still fires.
        if (history.isEmpty()) {
            String fallback = request.messages().stream()
                    .reduce((first, second) -> second)
                    .map(OpenAiDtos.ChatCompletionRequest.Message::content)
                    .orElse("Hello");
            history = List.of(new HrOnboardingAgent.Turn("user", fallback));
        }

        String chunkId = "chatcmpl-" + UUID.randomUUID();
        long created = Instant.now().getEpochSecond();
        String model = request.model() == null ? "brain" : request.model();

        ctx.contentType("text/event-stream");
        ctx.header("Cache-Control", "no-cache");
        ctx.header("Connection", "keep-alive");

        ServletOutputStream out = ctx.outputStream();
        CompletableFuture<Void> done = new CompletableFuture<>();
        ctx.future(() -> done);

        TokenStream stream = agent.chat(history);
        stream.onPartialResponse(partial -> writeChunk(out, chunkId, created, model, stripMarkdown(partial), null))
                .onPartialThinking(thinking -> log.debug("model thinking (not sent to TTS): {}", thinking.text()))
                .onPartialToolCall(call -> log.debug("partial tool call (not sent to TTS): {}", call.name()))
                .onToolExecuted(execution -> log.debug(
                        "tool executed (not sent to TTS): {} -> {}",
                        execution.request().name(), execution.result()
                ))
                .onCompleteResponse(complete -> {
                    writeChunk(out, chunkId, created, model, null, "stop");
                    writeRaw(out, "data: [DONE]\n\n");
                    closeQuietly(out);
                    done.complete(null);
                })
                .onError(error -> {
                    log.error("LLM streaming error", error);
                    closeQuietly(out);
                    done.completeExceptionally(error);
                })
                .start();
    }

    /**
     * Defensive safety net: the system prompt tells the model never to use
     * markdown, but it occasionally slips through (e.g. **bold**). Strip the
     * common markers rather than let a TTS engine try to pronounce them.
     */
    private String stripMarkdown(String text) {
        return text.replaceAll("[*_`#]", "");
    }

    private void writeChunk(
            ServletOutputStream out, String id, long created, String model, String content, String finishReason
    ) {
        var delta = new OpenAiDtos.ChatCompletionChunk.Delta(content == null ? null : "assistant", content);
        var choice = new OpenAiDtos.ChatCompletionChunk.Choice(0, delta, finishReason);
        var chunk = new OpenAiDtos.ChatCompletionChunk(id, "chat.completion.chunk", created, model, List.of(choice));
        try {
            String json = objectMapper.writeValueAsString(chunk);
            writeRaw(out, "data: " + json + "\n\n");
        } catch (IOException e) {
            log.warn("failed to write SSE chunk", e);
        }
    }

    private void writeRaw(ServletOutputStream out, String data) {
        try {
            out.write(data.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            out.flush();
        } catch (IOException e) {
            log.warn("failed to write to SSE stream (client likely disconnected)", e);
        }
    }

    private void closeQuietly(ServletOutputStream out) {
        try {
            out.close();
        } catch (IOException ignored) {
            // client already gone
        }
    }
}
