package dev.hrai.jobposting.resume;

import dev.hrai.jobposting.agent.ResumeScorer;
import dev.hrai.jobposting.model.Candidate;
import dev.hrai.jobposting.model.Posting;
import dev.hrai.jobposting.model.ResumeScore;
import dev.hrai.jobposting.store.CandidateStore;
import dev.hrai.jobposting.store.PostingStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Watches each open posting's resume-intake folder (one subfolder per
 * posting, under the configured inbox root) and scores whatever shows up --
 * this is the handoff point for the planned Gmail-monitoring agent, which
 * will drop CV attachments into these folders; until that exists, HR or
 * anyone else can drop files in manually.
 *
 * A file that fails to extract/score is moved to a {@code failed/}
 * subfolder and logged rather than retried forever or silently dropped.
 */
public final class ResumeInboxWatcher {

    private static final Logger log = LoggerFactory.getLogger(ResumeInboxWatcher.class);

    private final Path inboxRoot;
    private final PostingStore postingStore;
    private final CandidateStore candidateStore;
    private final ResumeScorer resumeScorer;
    private final Map<WatchKey, String> postingIdByWatchKey = new ConcurrentHashMap<>();
    private WatchService watchService;

    public ResumeInboxWatcher(Path inboxRoot, PostingStore postingStore, CandidateStore candidateStore, ResumeScorer resumeScorer) {
        this.inboxRoot = inboxRoot;
        this.postingStore = postingStore;
        this.candidateStore = candidateStore;
        this.resumeScorer = resumeScorer;
    }

    /** Starts the background watch thread and re-registers every currently-open posting. */
    public void start() throws IOException {
        watchService = inboxRoot.getFileSystem().newWatchService();
        Files.createDirectories(inboxRoot);

        for (Posting posting : postingStore.findOpen()) {
            watchPosting(posting);
        }

        Thread thread = new Thread(this::run, "resume-inbox-watcher");
        thread.setDaemon(true);
        thread.start();
    }

    /** Registers a newly-opened posting's folder, creating it and catching up on any files already in it. */
    public void watchPosting(Posting posting) {
        try {
            Path dir = Path.of(posting.inboxDir());
            Files.createDirectories(dir);
            Files.createDirectories(dir.resolve("processed"));
            Files.createDirectories(dir.resolve("failed"));

            WatchKey key = dir.register(watchService, StandardWatchEventKinds.ENTRY_CREATE);
            postingIdByWatchKey.put(key, posting.id());

            try (var files = Files.list(dir)) {
                files.filter(Files::isRegularFile).forEach(f -> processResumeFile(posting.id(), f));
            }
        } catch (IOException e) {
            log.error("failed to start watching inbox for posting {}", posting.id(), e);
        }
    }

    private void run() {
        while (true) {
            WatchKey key;
            try {
                key = watchService.take();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }

            String postingId = postingIdByWatchKey.get(key);
            if (postingId != null) {
                for (WatchEvent<?> event : key.pollEvents()) {
                    if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE) {
                        Path dir = (Path) key.watchable();
                        Path file = dir.resolve((Path) event.context());
                        if (Files.isRegularFile(file)) {
                            processResumeFile(postingId, file);
                        }
                    }
                }
            }
            key.reset();
        }
    }

    private void processResumeFile(String postingId, Path file) {
        waitForStableFile(file);
        Posting posting = postingStore.find(postingId);
        if (posting == null) {
            log.warn("resume {} dropped for unknown posting {}, leaving it in place", file, postingId);
            return;
        }

        try {
            String text;
            try (InputStream in = Files.newInputStream(file)) {
                text = ResumeTextExtractor.extract(in, Files.probeContentType(file), file.getFileName().toString());
            }
            ResumeScore score = resumeScorer.score(posting.jobPosting(), text);
            Candidate candidate = Candidate.pending(
                    UUID.randomUUID().toString(), postingId, file.getFileName().toString(), score
            );
            candidateStore.save(candidate);
            moveTo(file, Path.of(posting.inboxDir()).resolve("processed"));
            log.info("scored resume {} for posting {} -> {}/100", file.getFileName(), postingId, score.fitScore());
        } catch (Exception e) {
            log.error("failed to process resume {} for posting {}", file, postingId, e);
            moveTo(file, Path.of(posting.inboxDir()).resolve("failed"));
        }
    }

    /** Polls file size until it stops changing, so we don't read a file mid-write. */
    private void waitForStableFile(Path file) {
        long lastSize = -1;
        for (int i = 0; i < 10; i++) {
            try {
                long size = Files.size(file);
                if (size == lastSize && size > 0) {
                    return;
                }
                lastSize = size;
                Thread.sleep(300);
            } catch (IOException | InterruptedException e) {
                return;
            }
        }
    }

    private void moveTo(Path file, Path targetDir) {
        try {
            Files.move(file, targetDir.resolve(file.getFileName()), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.warn("failed to move {} into {}", file, targetDir, e);
        }
    }
}
