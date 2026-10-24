package dev.hrai.jobposting.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.hrai.jobposting.agent.ResumeScorer;
import dev.hrai.jobposting.model.JobPosting;
import dev.hrai.jobposting.model.ResumeScore;
import dev.hrai.jobposting.resume.ResumeTextExtractor;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.javalin.http.UploadedFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@code POST /v1/score-resume} -- multipart: a {@code resume} file (PDF or
 * plain text) and a {@code posting} form field (the JobPosting JSON to
 * score against, exactly as returned by /v1/draft-posting). There's no
 * backend storage of postings, so the client sends the one it has.
 */
public final class ScoreResumeHandler implements Handler {

    private static final Logger log = LoggerFactory.getLogger(ScoreResumeHandler.class);

    private final ResumeScorer scorer;
    private final ObjectMapper objectMapper;

    public ScoreResumeHandler(ResumeScorer scorer, ObjectMapper objectMapper) {
        this.scorer = scorer;
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(Context ctx) throws Exception {
        UploadedFile resumeFile = ctx.uploadedFile("resume");
        String postingJson = ctx.formParam("posting");

        if (resumeFile == null) {
            ctx.status(400).json(new DraftPostingHandler.ErrorResponse("resume file is required"));
            return;
        }
        if (postingJson == null || postingJson.isBlank()) {
            ctx.status(400).json(new DraftPostingHandler.ErrorResponse("posting field is required"));
            return;
        }

        try {
            JobPosting posting = objectMapper.readValue(postingJson, JobPosting.class);
            String resumeText = ResumeTextExtractor.extract(
                    resumeFile.content(), resumeFile.contentType(), resumeFile.filename()
            );
            ResumeScore score = scorer.score(posting, resumeText);
            ctx.json(score);
        } catch (Exception e) {
            log.error("failed to score resume", e);
            ctx.status(502).json(new DraftPostingHandler.ErrorResponse("failed to score resume: " + e.getMessage()));
        }
    }
}
