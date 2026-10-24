package dev.hrai.jobposting.http;

import dev.hrai.jobposting.email.EmailSender;
import dev.hrai.jobposting.model.Candidate;
import dev.hrai.jobposting.model.Posting;
import dev.hrai.jobposting.store.CandidateStore;
import dev.hrai.jobposting.store.PostingStore;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@code POST /v1/postings/{id}/candidates/{candidateId}/approve} and
 * {@code .../reject} -- the only way a candidate's status changes; nothing
 * here is automatic. Approval sends the screening-interview email via
 * {@link EmailSender}; rejection does nothing further.
 */
public final class CandidateDecisionHandler implements Handler {

    private static final Logger log = LoggerFactory.getLogger(CandidateDecisionHandler.class);

    private final PostingStore postingStore;
    private final CandidateStore candidateStore;
    private final EmailSender emailSender;
    private final Candidate.Status decision;

    public CandidateDecisionHandler(
            PostingStore postingStore, CandidateStore candidateStore, EmailSender emailSender, Candidate.Status decision
    ) {
        this.postingStore = postingStore;
        this.candidateStore = candidateStore;
        this.emailSender = emailSender;
        this.decision = decision;
    }

    @Override
    public void handle(Context ctx) {
        String postingId = ctx.pathParam("id");
        String candidateId = ctx.pathParam("candidateId");

        Candidate candidate = candidateStore.find(postingId, candidateId);
        if (candidate == null) {
            ctx.status(404).json(new DraftPostingHandler.ErrorResponse("no such candidate"));
            return;
        }

        if (decision == Candidate.Status.APPROVED) {
            String email = candidate.score().candidateEmail();
            if (email == null || email.isBlank()) {
                ctx.status(422).json(new DraftPostingHandler.ErrorResponse(
                        "this resume has no email address on file -- add one manually before approving"
                ));
                return;
            }
            Posting posting = postingStore.find(postingId);
            try {
                emailSender.sendScreeningInvite(email, candidate.score().candidateName(), posting.jobPosting().title());
            } catch (Exception e) {
                log.error("failed to send screening invite to {}", email, e);
                ctx.status(502).json(new DraftPostingHandler.ErrorResponse(
                        "approved, but the screening-interview email failed to send: " + e.getMessage()
                ));
                return;
            }
        }

        Candidate updated = candidate.decided(decision);
        candidateStore.save(updated);
        ctx.json(updated);
    }
}
