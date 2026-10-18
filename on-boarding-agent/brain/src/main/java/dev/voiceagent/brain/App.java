package dev.voiceagent.brain;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import dev.voiceagent.brain.agent.HrOnboardingAgent;
import dev.voiceagent.brain.http.AdminSessionsHandler;
import dev.voiceagent.brain.http.ChatCompletionsHandler;
import dev.voiceagent.brain.http.EmployeeInfoHandler;
import dev.voiceagent.brain.http.TokenHandler;
import dev.voiceagent.brain.rag.KnowledgeBase;
import dev.voiceagent.brain.rag.PolicySearchTool;
import dev.voiceagent.brain.sessions.SessionStore;
import dev.voiceagent.brain.tools.EscalationTool;
import io.javalin.Javalin;

import java.nio.file.Path;

/**
 * HR onboarding agent brain: LangChain4j conversation logic + RAG + tools,
 * exposed to the LiveKit glue layer (../glue) as an OpenAI-compatible
 * streaming endpoint.
 */
public final class App {

    private App() {
    }

    public static void main(String[] args) {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        String groqBaseUrl = System.getenv().getOrDefault("GROQ_BASE_URL", "https://api.groq.com/openai/v1");
        String groqApiKey = System.getenv("GROQ_API_KEY");
        String groqModel = System.getenv().getOrDefault("GROQ_MODEL", "openai/gpt-oss-20b");
        String ollamaBaseUrl = System.getenv().getOrDefault("OLLAMA_BASE_URL", "http://localhost:11434");
        String ollamaEmbeddingModel = System.getenv().getOrDefault("OLLAMA_EMBEDDING_MODEL", "nomic-embed-text");
        String qdrantHost = System.getenv().getOrDefault("QDRANT_HOST", "localhost");
        int qdrantPort = Integer.parseInt(System.getenv().getOrDefault("QDRANT_GRPC_PORT", "6334"));
        Path knowledgeBaseDir = Path.of(System.getenv().getOrDefault("KNOWLEDGE_BASE_DIR", "brain/knowledge-base"));
        String livekitUrl = System.getenv("LIVEKIT_URL");
        String livekitApiKey = System.getenv("LIVEKIT_API_KEY");
        String livekitApiSecret = System.getenv("LIVEKIT_API_SECRET");

        if (groqApiKey == null || groqApiKey.isBlank()) {
            throw new IllegalStateException("GROQ_API_KEY environment variable is required");
        }
        if (livekitUrl == null || livekitApiKey == null || livekitApiSecret == null) {
            throw new IllegalStateException(
                    "LIVEKIT_URL, LIVEKIT_API_KEY, and LIVEKIT_API_SECRET environment variables are required"
            );
        }

        EmbeddingModel embeddingModel = OllamaEmbeddingModel.builder()
                .baseUrl(ollamaBaseUrl)
                .modelName(ollamaEmbeddingModel)
                .build();

        KnowledgeBase knowledgeBase = new KnowledgeBase(embeddingModel, qdrantHost, qdrantPort);
        knowledgeBase.ingest(knowledgeBaseDir);
        PolicySearchTool policySearchTool = new PolicySearchTool(knowledgeBase);
        EscalationTool escalationTool = new EscalationTool();

        HrOnboardingAgent agent = new HrOnboardingAgent(
                groqBaseUrl, groqApiKey, groqModel, policySearchTool, escalationTool
        );
        ObjectMapper objectMapper = new ObjectMapper()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        SessionStore sessionStore = new SessionStore();
        AdminSessionsHandler adminSessionsHandler = new AdminSessionsHandler(sessionStore, objectMapper);

        Javalin app = Javalin.create(config -> config.bundledPlugins.enableCors(cors -> cors.addRule(rule -> rule.anyHost())));
        app.get("/health", ctx -> ctx.result("ok"));
        app.post("/v1/chat/completions", new ChatCompletionsHandler(agent, objectMapper));
        app.get("/api/token", new TokenHandler(livekitApiKey, livekitApiSecret, livekitUrl, sessionStore, objectMapper));
        app.get("/api/employee-info", new EmployeeInfoHandler(sessionStore));
        app.post("/api/admin/sessions", adminSessionsHandler::create);
        app.get("/api/admin/sessions", adminSessionsHandler::list);
        app.get("/api/admin/sessions/{id}", adminSessionsHandler::get);

        app.start(port);
    }
}
