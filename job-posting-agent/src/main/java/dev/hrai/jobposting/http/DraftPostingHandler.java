package dev.hrai.jobposting.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.hrai.jobposting.agent.JobPostingDrafter;
import dev.hrai.jobposting.model.JobPosting;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** {@code POST /v1/draft-posting} -- turns a free-text role prompt into a {@link JobPosting} draft. */
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
        if (request.prompt() == null || request.prompt().isBlank()) {
            ctx.status(400).json(new ErrorResponse("prompt must not be blank"));
            return;
        }
        try {
            JobPosting posting = drafter.draft(request.prompt(), request.applicationEmail());
            ctx.json(posting);
        } catch (Exception e) {
            log.error("failed to draft job posting", e);
            ctx.status(502).json(new ErrorResponse("failed to draft job posting: " + e.getMessage()));
        }
    }

    public record Request(String prompt, String applicationEmail) {
    }

    public record ErrorResponse(String error) {
    }
}
