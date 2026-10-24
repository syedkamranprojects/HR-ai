package dev.hrai.jobposting.model;

import java.time.Instant;

/**
 * One resume picked up from a posting's inbox folder and scored against it.
 * HR approves or rejects explicitly -- nothing here acts on its own beyond
 * scoring. Approval triggers a screening-interview email (see EmailSender);
 * rejection does nothing further.
 */
public record Candidate(
        String id,
        String postingId,
        String resumeFilename,
        ResumeScore score,
        Status status,
        String scoredAt,
        String decidedAt
) {
    public enum Status {
        PENDING, APPROVED, REJECTED
    }

    public static Candidate pending(String id, String postingId, String resumeFilename, ResumeScore score) {
        return new Candidate(id, postingId, resumeFilename, score, Status.PENDING, Instant.now().toString(), null);
    }

    public Candidate decided(Status status) {
        return new Candidate(id, postingId, resumeFilename, score, status, scoredAt, Instant.now().toString());
    }
}
