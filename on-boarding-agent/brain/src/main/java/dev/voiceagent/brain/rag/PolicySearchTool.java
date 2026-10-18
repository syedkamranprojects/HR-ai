package dev.voiceagent.brain.rag;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/** Exposes {@link KnowledgeBase} search to the LLM as a tool call. */
public final class PolicySearchTool {

    private static final Logger log = LoggerFactory.getLogger(PolicySearchTool.class);

    private final KnowledgeBase knowledgeBase;

    public PolicySearchTool(KnowledgeBase knowledgeBase) {
        this.knowledgeBase = knowledgeBase;
    }

    @Tool("Search Horizon Labs company policies (time off, benefits, code of conduct, "
            + "company overview) for information relevant to the employee's question. "
            + "Always use this before answering any question about company policy, "
            + "benefits, or the company itself -- never guess or make up policy details.")
    public String searchCompanyPolicies(@P("the question or topic to search for") String query) {
        log.info("searchCompanyPolicies called with query: {}", query);
        try {
            List<String> matches = knowledgeBase.search(query, 3);
            log.info("searchCompanyPolicies found {} matches", matches.size());
            if (matches.isEmpty()) {
                return "No relevant company policy information was found for this query.";
            }
            return String.join("\n\n---\n\n", matches);
        } catch (Throwable t) {
            log.error("searchCompanyPolicies failed", t);
            throw t;
        }
    }
}
