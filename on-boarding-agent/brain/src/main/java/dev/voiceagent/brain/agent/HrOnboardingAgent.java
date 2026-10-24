package dev.voiceagent.brain.agent;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;
import dev.voiceagent.brain.rag.PolicySearchTool;
import dev.voiceagent.brain.tools.EscalationTool;

import java.util.List;

/**
 * HR onboarding conversational agent. Conversation history is supplied by
 * the caller on every turn (the LiveKit glue layer resends the full
 * OpenAI-style message history each request); this class rebuilds a
 * ChatMemory from it per call rather than keeping memory across requests.
 */
public final class HrOnboardingAgent {

    private static final String COMPANY_NAME = "Centegy Technologies";

    private static final String SYSTEM_PROMPT = """
            You are a warm, concise HR onboarding assistant for """ + COMPANY_NAME + """
            , speaking to a new employee out loud on their first day. You
            always know and should say the company's name, """ + COMPANY_NAME + """
            , whenever it's relevant -- never refer to it only as "the
            company" or "your company". Keep responses short and
            conversational -- this is a voice conversation, not a chat window.
            Never use markdown formatting of any kind (no asterisks, bullet
            points, headings, or bold/italic) and never use numbered lists --
            say everything as plain, natural sentences a text-to-speech engine
            can read aloud correctly. Always respond in English, even if the
            employee speaks, or is transcribed, in a different language --
            understand whatever language they use, but always answer in
            English.

            Greet the new employee and welcome them to """ + COMPANY_NAME + """
            . If the conversation tells you the employee's actual name (for
            example in these instructions or earlier in the conversation),
            address them by that name. Otherwise you do not know their name,
            so never use a placeholder like [Employee Name] -- just address
            them generically ("welcome aboard", "welcome to the team").
            Briefly introduce yourself, and offer a short overview of what
            you can help with: company policies, benefits, and general
            first-day questions. Then let them ask questions.

            You have a tool, searchCompanyPolicies, that looks up real company
            policy and benefits information (what the company does, offices,
            benefits, time off, conduct, etc). Always use it before answering
            any question about the company, its policies, or its benefits --
            never guess or invent details beyond the company name itself.

            You also have a tool, escalateToHuman, that hands the conversation
            off to a real HR person and gives you a reference number. Use it
            whenever the employee explicitly asks to talk to a human or to HR,
            and also when searchCompanyPolicies has nothing relevant and the
            employee still needs an answer. When you use it, read the reference
            number back to them clearly so they can note it down.
            """;

    private final StreamingChatModel model;
    private final PolicySearchTool policySearchTool;
    private final EscalationTool escalationTool;

    public HrOnboardingAgent(
            String groqBaseUrl, String groqApiKey, String modelName,
            PolicySearchTool policySearchTool, EscalationTool escalationTool
    ) {
        this.model = OpenAiStreamingChatModel.builder()
                .baseUrl(groqBaseUrl)
                .apiKey(groqApiKey)
                .modelName(modelName)
                .build();
        this.policySearchTool = policySearchTool;
        this.escalationTool = escalationTool;
    }

    public TokenStream chat(List<Turn> history) {
        if (history.isEmpty()) {
            throw new IllegalArgumentException("history must contain at least one message");
        }

        // Kept small deliberately: RAG tool results (policy-doc chunks) get
        // embedded into history and resent on every subsequent turn, so a
        // generous window makes token usage balloon as a conversation goes
        // on -- which is what was pushing us over Groq's free-tier 8000
        // tokens/minute cap. 12 messages (~6 turns) keeps enough context
        // for natural conversation continuity without that growth.
        ChatMemory memory = MessageWindowChatMemory.withMaxMessages(12);
        for (Turn turn : history.subList(0, history.size() - 1)) {
            memory.add(turn.isUser() ? UserMessage.from(turn.content()) : AiMessage.from(turn.content()));
        }
        String latestUserMessage = history.get(history.size() - 1).content();

        Assistant assistant = AiServices.builder(Assistant.class)
                .streamingChatModel(model)
                .chatMemory(memory)
                .tools(policySearchTool, escalationTool)
                .build();

        return assistant.chat(latestUserMessage);
    }

    private interface Assistant {
        @SystemMessage(SYSTEM_PROMPT)
        TokenStream chat(String userMessage);
    }

    public record Turn(String role, String content) {
        public boolean isUser() {
            return "user".equals(role);
        }
    }
}
