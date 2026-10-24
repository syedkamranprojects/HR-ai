package dev.hrai.jobposting.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.hrai.jobposting.agent.JobPostingDrafter;
import dev.hrai.jobposting.model.JobPosting;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * {@code POST /v1/draft-posting} -- turns a conversation (initial role
 * description, then optional refinement turns) into a {@link JobPosting}
 * draft. The client resends the full message history each call, same as
 * on-boarding-agent/brain's chat endpoint.
 */
public final class DraftPostingHandler implements Handler {

    private static final Logger log = LoggerFactory.getLogger(DraftPostingHandler.class);

    private final JobPostingDrafter drafter;
    private final ObjectMapper objectMapper;

    public DraftPostingHandler(JobPostingDrafter drafter, ObjectMapper objectMapper) {
        this.drafter = drafter;
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(Context ctx) {
        Request request = ctx.bodyAsClass(Request.class);
        if (request.messages() == null || request.messages().isEmpty()) {
            ctx.status(400).json(new ErrorResponse("messages must not be empty"));
            return;
        }
        try {
            List<JobPostingDrafter.Turn> history = request.messages().stream()
                    .map(m -> new JobPostingDrafter.Turn(m.role(), m.content()))
                    .toList();
            JobPosting posting = drafter.draft(history, request.applicationEmail());
            ctx.json(posting);
        } catch (Exception e) {
            log.error("failed to draft job posting", e);
            ctx.status(502).json(new ErrorResponse("failed to draft job posting: " + e.getMessage()));
        }
    }

    public record Request(List<Message> messages, String applicationEmail) {
        public record Message(String role, String content) {
        }
    }

    public record ErrorResponse(String error) {
    }
}
