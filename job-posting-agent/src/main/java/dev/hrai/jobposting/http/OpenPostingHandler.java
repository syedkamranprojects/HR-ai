package dev.hrai.jobposting.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.hrai.jobposting.model.JobPosting;
import dev.hrai.jobposting.model.Posting;
import dev.hrai.jobposting.resume.ResumeInboxWatcher;
import dev.hrai.jobposting.store.PostingStore;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.UUID;

/**
 * {@code POST /v1/postings} -- opens a drafted posting for applications:
 * persists it, creates its resume-intake folder, and starts watching it.
 */
public final class OpenPostingHandler implements Handler {

    private static final Logger log = LoggerFactory.getLogger(OpenPostingHandler.class);

    private final PostingStore postingStore;
    private final ResumeInboxWatcher watcher;
    private final Path inboxRoot;
    private final ObjectMapper objectMapper;

    public OpenPostingHandler(PostingStore postingStore, ResumeInboxWatcher watcher, Path inboxRoot, ObjectMapper objectMapper) {
        this.postingStore = postingStore;
        this.watcher = watcher;
        this.inboxRoot = inboxRoot;
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(Context ctx) {
        try {
            JobPosting jobPosting = objectMapper.readValue(ctx.body(), JobPosting.class);
            String id = UUID.randomUUID().toString();
            String inboxDir = inboxRoot.resolve(id).toAbsolutePath().toString();

            Posting posting = Posting.open(id, jobPosting, inboxDir);
            postingStore.save(posting);
            watcher.watchPosting(posting);

            ctx.json(posting);
        } catch (Exception e) {
            log.error("failed to open posting for applications", e);
            ctx.status(502).json(new DraftPostingHandler.ErrorResponse("failed to open posting: " + e.getMessage()));
        }
    }
}
