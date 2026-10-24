package dev.voiceagent.brain.http;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * One structured record per conversation turn, emitted as a single JSON log
 * line (see {@link ChatCompletionsHandler}). This is the foundation for the
 * observability framework discussed with the user: everything downstream
 * (tool-selection accuracy checks, latency/cost analysis, groundedness
 * evals) is built by parsing these lines, so what's captured here matters
 * more than how it's displayed.
 */
public final class TurnLog {

    public final String requestId;
    public final String timestamp = Instant.now().toString();
    public final String input;
    public final List<ToolCallRecord> toolCalls = new ArrayList<>();
    private final long startNanos = System.nanoTime();
    public String output = "";
    public Long firstTokenLatencyMs;
    public Long totalLatencyMs;
    /** Rough, tokenizer-free estimate (~4 chars/token for English) -- good enough for cost trending, not billing. */
    public Integer approxOutputTokens;
    public boolean failed;

    public TurnLog(String requestId, String input) {
        this.requestId = requestId;
        this.input = input;
    }

    public void recordFirstToken() {
        if (firstTokenLatencyMs == null) {
            firstTokenLatencyMs = elapsedMs();
        }
    }

    public void recordToolCall(String name, String arguments, String result, boolean toolFailed) {
        toolCalls.add(new ToolCallRecord(name, arguments, result, toolFailed));
    }

    public void finish(boolean turnFailed) {
        this.failed = turnFailed;
        this.totalLatencyMs = elapsedMs();
        this.approxOutputTokens = output.length() / 4;
    }

    private long elapsedMs() {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    public record ToolCallRecord(String name, String arguments, String result, boolean failed) {
    }
}
