package dev.hrai.jobposting.model;

import java.time.Instant;

/**
 * A job posting HR has opened for applications: persisted (survives
 * restarts) and backed by its own resume-intake folder, because unlike the
 * drafting chat (session-only), incoming resumes can arrive at any time,
 * not just while HR has the page open.
 */
public record Posting(
        String id,
        JobPosting jobPosting,
        Status status,
        String inboxDir,
        String createdAt
) {
    public enum Status {
        OPEN, CLOSED
    }

    public static Posting open(String id, JobPosting jobPosting, String inboxDir) {
        return new Posting(id, jobPosting, Status.OPEN, inboxDir, Instant.now().toString());
    }

    public Posting closed() {
        return new Posting(id, jobPosting, Status.CLOSED, inboxDir, createdAt);
    }
}
