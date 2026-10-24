package dev.hrai.jobposting.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.hrai.jobposting.model.Candidate;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class CandidateStore {

    private final Path dataDir;
    private final ObjectMapper objectMapper;
    private final Map<String, JsonFileStore<Candidate>> byPosting = new ConcurrentHashMap<>();

    public CandidateStore(Path dataDir, ObjectMapper objectMapper) {
        this.dataDir = dataDir;
        this.objectMapper = objectMapper;
    }

    public void save(Candidate candidate) {
        storeFor(candidate.postingId()).save(candidate.id(), candidate);
    }

    public Candidate find(String postingId, String candidateId) {
        return storeFor(postingId).find(candidateId);
    }

    public List<Candidate> findByPosting(String postingId) {
        return storeFor(postingId).findAll();
    }

    private JsonFileStore<Candidate> storeFor(String postingId) {
        return byPosting.computeIfAbsent(
                postingId,
                id -> new JsonFileStore<>(dataDir.resolve("candidates").resolve(id), Candidate.class, objectMapper)
        );
    }
}
