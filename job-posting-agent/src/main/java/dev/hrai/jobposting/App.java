package dev.hrai.jobposting;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.hrai.jobposting.agent.JobPostingDrafter;
import dev.hrai.jobposting.agent.ResumeScorer;
import dev.hrai.jobposting.email.EmailSender;
import dev.hrai.jobposting.email.LoggingEmailSender;
import dev.hrai.jobposting.email.SmtpEmailSender;
import dev.hrai.jobposting.http.CandidateDecisionHandler;
import dev.hrai.jobposting.http.DraftPostingHandler;
import dev.hrai.jobposting.http.OpenPostingHandler;
import dev.hrai.jobposting.http.PostingsHandler;
import dev.hrai.jobposting.http.ScoreResumeHandler;
import dev.hrai.jobposting.model.Candidate;
import dev.hrai.jobposting.resume.ResumeInboxWatcher;
import dev.hrai.jobposting.store.CandidateStore;
import dev.hrai.jobposting.store.PostingStore;
import io.javalin.Javalin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

/**
 * Job posting agent: HR gives a free-text role description, this drafts a
 * structured posting for HR to review; HR can open a posting for
 * applications, which watches a dedicated inbox folder (the planned
 * Gmail-monitoring agent drops resumes there; anyone/anything else can too)
 * and scores whatever shows up, purely advisorily. HR approves or rejects
 * each candidate explicitly; approval sends a screening-interview email.
 */
public final class App {

    private static final Logger log = LoggerFactory.getLogger(App.class);

    private App() {
    }

    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8081"));
        String groqBaseUrl = System.getenv().getOrDefault("GROQ_BASE_URL", "https://api.groq.com/openai/v1");
        String groqApiKey = System.getenv("GROQ_API_KEY");
        String groqModel = System.getenv().getOrDefault("GROQ_MODEL", "openai/gpt-oss-20b");
        Path dataDir = Path.of(System.getenv().getOrDefault("DATA_DIR", "data"));
        Path inboxRoot = Path.of(System.getenv().getOrDefault("RESUME_INBOX_DIR", "inbox"));

        if (groqApiKey == null || groqApiKey.isBlank()) {
            throw new IllegalStateException("GROQ_API_KEY environment variable is required");
        }

        JobPostingDrafter drafter = new JobPostingDrafter(groqBaseUrl, groqApiKey, groqModel);
        ResumeScorer resumeScorer = new ResumeScorer(groqBaseUrl, groqApiKey, groqModel);
        ObjectMapper objectMapper = new ObjectMapper()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

        PostingStore postingStore = new PostingStore(dataDir, objectMapper);
        CandidateStore candidateStore = new CandidateStore(dataDir, objectMapper);

        EmailSender emailSender = buildEmailSender();

        ResumeInboxWatcher watcher = new ResumeInboxWatcher(inboxRoot, postingStore, candidateStore, resumeScorer);
        watcher.start();

        PostingsHandler postingsHandler = new PostingsHandler(postingStore, candidateStore);

        Javalin app = Javalin.create(config -> config.bundledPlugins.enableCors(cors -> cors.addRule(rule -> rule.anyHost())));
        app.get("/health", ctx -> ctx.result("ok"));
        app.post("/v1/draft-posting", new DraftPostingHandler(drafter, objectMapper));
        app.post("/v1/score-resume", new ScoreResumeHandler(resumeScorer, objectMapper));
        app.post("/v1/postings", new OpenPostingHandler(postingStore, watcher, inboxRoot, objectMapper));
        app.get("/v1/postings", postingsHandler::list);
        app.get("/v1/postings/{id}/candidates", postingsHandler::listCandidates);
        app.post(
                "/v1/postings/{id}/candidates/{candidateId}/approve",
                new CandidateDecisionHandler(postingStore, candidateStore, emailSender, Candidate.Status.APPROVED)
        );
        app.post(
                "/v1/postings/{id}/candidates/{candidateId}/reject",
                new CandidateDecisionHandler(postingStore, candidateStore, emailSender, Candidate.Status.REJECTED)
        );

        app.start(port);
    }

    private static EmailSender buildEmailSender() {
        String host = System.getenv("SMTP_HOST");
        String username = System.getenv("SMTP_USERNAME");
        String password = System.getenv("SMTP_PASSWORD");

        if (host != null && !host.isBlank() && username != null && password != null) {
            int port = Integer.parseInt(System.getenv().getOrDefault("SMTP_PORT", "587"));
            String fromAddress = System.getenv().getOrDefault("SMTP_FROM_ADDRESS", username);
            String fromName = System.getenv().getOrDefault("SMTP_FROM_NAME", "Centegy Technologies HR");
            log.info("SMTP configured ({}) -- approval emails will actually be sent", host);
            return new SmtpEmailSender(host, port, username, password, fromAddress, fromName);
        }

        log.warn("SMTP_HOST/SMTP_USERNAME/SMTP_PASSWORD not set -- approval emails will only be logged, not sent");
        return new LoggingEmailSender();
    }
}
