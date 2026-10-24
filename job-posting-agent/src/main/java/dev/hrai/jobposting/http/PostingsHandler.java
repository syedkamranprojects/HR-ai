package dev.hrai.jobposting.http;

import dev.hrai.jobposting.model.Candidate;
import dev.hrai.jobposting.model.Posting;
import dev.hrai.jobposting.store.CandidateStore;
import dev.hrai.jobposting.store.PostingStore;
import io.javalin.http.Context;

import java.util.List;

/** {@code GET /v1/postings} and {@code GET /v1/postings/{id}/candidates}. */
public final class PostingsHandler {

    private final PostingStore postingStore;
    private final CandidateStore candidateStore;

    public PostingsHandler(PostingStore postingStore, CandidateStore candidateStore) {
        this.postingStore = postingStore;
        this.candidateStore = candidateStore;
    }

    public void list(Context ctx) {
        ctx.json(postingStore.findOpen());
    }

    public void listCandidates(Context ctx) {
        String postingId = ctx.pathParam("id");
        Posting posting = postingStore.find(postingId);
        if (posting == null) {
            ctx.status(404).json(new DraftPostingHandler.ErrorResponse("no such posting"));
            return;
        }
        List<Candidate> candidates = candidateStore.findByPosting(postingId).stream()
                .sorted((a, b) -> b.scoredAt().compareTo(a.scoredAt()))
                .toList();
        ctx.json(candidates);
    }
}
