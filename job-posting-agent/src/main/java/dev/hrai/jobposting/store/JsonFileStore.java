package dev.hrai.jobposting.store;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Minimal persistence: one JSON file per record, under {@code dir}. No
 * database -- matches this project's preference for no new infrastructure
 * until something actually needs it, and candidate/posting volumes here are
 * small. Not safe for high-concurrency writes to the *same* record, but
 * posting/candidate records are each written by only one actor at a time in
 * practice (the watcher creates a candidate once; HR decides on it once).
 */
final class JsonFileStore<T> {

    private final Path dir;
    private final Class<T> type;
    private final ObjectMapper objectMapper;

    JsonFileStore(Path dir, Class<T> type, ObjectMapper objectMapper) {
        this.dir = dir;
        this.type = type;
        this.objectMapper = objectMapper;
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    synchronized void save(String id, T value) {
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(file(id).toFile(), value);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    T find(String id) {
        Path file = file(id);
        if (!Files.exists(file)) {
            return null;
        }
        try {
            return objectMapper.readValue(file.toFile(), type);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    List<T> findAll() {
        try (Stream<Path> files = Files.list(dir)) {
            List<T> result = new ArrayList<>();
            for (Path file : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                result.add(objectMapper.readValue(file.toFile(), type));
            }
            return result;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Path file(String id) {
        return dir.resolve(id + ".json");
    }
}
