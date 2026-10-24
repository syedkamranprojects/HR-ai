package dev.hrai.jobposting.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.hrai.jobposting.model.Posting;

import java.nio.file.Path;
import java.util.List;

public final class PostingStore {

    private final JsonFileStore<Posting> store;

    public PostingStore(Path dataDir, ObjectMapper objectMapper) {
        this.store = new JsonFileStore<>(dataDir.resolve("postings"), Posting.class, objectMapper);
    }

    public void save(Posting posting) {
        store.save(posting.id(), posting);
    }

    public Posting find(String id) {
        return store.find(id);
    }

    public List<Posting> findAll() {
        return store.findAll();
    }

    public List<Posting> findOpen() {
        return store.findAll().stream().filter(p -> p.status() == Posting.Status.OPEN).toList();
    }
}
