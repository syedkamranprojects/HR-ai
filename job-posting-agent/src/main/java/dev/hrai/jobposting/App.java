package dev.hrai.jobposting;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.hrai.jobposting.agent.JobPostingDrafter;
import dev.hrai.jobposting.agent.ResumeScorer;
import dev.hrai.jobposting.http.DraftPostingHandler;
import dev.hrai.jobposting.http.ScoreResumeHandler;
import io.javalin.Javalin;

/**
 * Job posting agent: HR gives a free-text role description, this drafts a
 * structured posting for HR to review and post manually, and scores
 * uploaded resumes against a posting's criteria -- purely advisory, HR
 * reviews every candidate. Email inbox monitoring is not yet built.
 */
public final class App {

    private App() {
    }

    public static void main(String[] args) {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8081"));
        String groqBaseUrl = System.getenv().getOrDefault("GROQ_BASE_URL", "https://api.groq.com/openai/v1");
        String groqApiKey = System.getenv("GROQ_API_KEY");
        String groqModel = System.getenv().getOrDefault("GROQ_MODEL", "openai/gpt-oss-20b");

        if (groqApiKey == null || groqApiKey.isBlank()) {
            throw new IllegalStateException("GROQ_API_KEY environment variable is required");
        }

        JobPostingDrafter drafter = new JobPostingDrafter(groqBaseUrl, groqApiKey, groqModel);
        ResumeScorer resumeScorer = new ResumeScorer(groqBaseUrl, groqApiKey, groqModel);
        ObjectMapper objectMapper = new ObjectMapper()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

        Javalin app = Javalin.create(config -> config.bundledPlugins.enableCors(cors -> cors.addRule(rule -> rule.anyHost())));
        app.get("/health", ctx -> ctx.result("ok"));
        app.post("/v1/draft-posting", new DraftPostingHandler(drafter, objectMapper));
        app.post("/v1/score-resume", new ScoreResumeHandler(resumeScorer, objectMapper));

        app.start(port);
    }
}
