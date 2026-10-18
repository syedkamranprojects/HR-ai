package dev.voiceagent.brain.http;

import java.util.List;

/**
 * Minimal subset of the OpenAI chat-completions wire format -- just enough
 * for LiveKit's openai.LLM plugin (pointed at a custom base_url) to talk to
 * this service as if it were an OpenAI-compatible endpoint.
 */
public final class OpenAiDtos {

    private OpenAiDtos() {
    }

    public record ChatCompletionRequest(String model, List<Message> messages, Boolean stream) {
        public record Message(String role, String content) {
        }
    }

    public record ChatCompletionChunk(
            String id,
            String object,
            long created,
            String model,
            List<Choice> choices
    ) {
        public record Choice(int index, Delta delta, String finish_reason) {
        }

        public record Delta(String role, String content) {
        }
    }
}
